#!/usr/bin/env bb
;; SPDX-License-Identifier: GPL-3.0-or-later

(ns anv.smoke
  "End-to-end smoke checks for the isolated Alfresco Node Vault Compose stack."
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io ByteArrayInputStream]
           [java.nio.file Files]
           [java.security MessageDigest SecureRandom]
           [java.util Random UUID]))

(defn env [name default]
  (or (System/getenv name) default))

(def alfresco (env "ALFRESCO_URL" "http://alfresco:8080"))
(def core "/alfresco/api/-default-/public/alfresco/versions/1")
(def api "/api/v1/vault")
(def cases ["s3-plain" "gridfs-plain" "s3-encrypted" "gridfs-encrypted"])
(def client (http/client {:connect-timeout 10000 :follow-redirects :never :version :http1.1}))

(defn log! [& messages]
  (apply println messages)
  (flush))

(defn text [data]
  (String. ^bytes data "UTF-8"))

(defn request!
  [base method path & [{:keys [data expected auth? headers]
                       :or {expected #{200} auth? true headers {}}}]]
  (let [body (cond
               (map? data) (json/generate-string data)
               (bytes? data) (ByteArrayInputStream. data)
               :else data)
        options (cond-> {:uri (str base path) :method method :client client
                         :as :bytes :throw false :timeout 60000
                         :headers (cond-> headers (map? data) (assoc "Content-Type" "application/json"))}
                  (some? body) (assoc :body body)
                  auth? (assoc :basic-auth [(env "ALFRESCO_USERNAME" "admin")
                                           (env "ALFRESCO_PASSWORD" "admin")]))
        {:keys [status body]} (http/request options)]
    (when-not (contains? expected status)
      (let [description (text body)]
        (throw (ex-info (format "%s %s%s: HTTP %s, expected %s: %s"
                                (str/upper-case (name method)) base path status expected
                                (subs description 0 (min 300 (count description))))
                        {:status status :method method :path path}))))
    body))

(defn read-json [data]
  (json/parse-string (text data) true))

(defn wait-for! [label check]
  (let [deadline (+ (System/nanoTime) (* 1000000000 (parse-long (env "SMOKE_WAIT_SECONDS" "900"))))]
    (loop [last-error nil]
      (when (>= (System/nanoTime) deadline)
        (throw (ex-info (str "Timed out waiting for " label ": " (some-> last-error ex-message))
                        {:service label} last-error)))
      (let [error (try (check) nil (catch Exception e e))]
        (if error
          (do (Thread/sleep 3000) (recur error))
          (log! "READY" label))))))

(defn hex [data]
  (apply str (map #(format "%02x" (bit-and 0xff %)) data)))

(defn digest [data]
  (hex (.digest (MessageDigest/getInstance "SHA-256") ^bytes data)))

(defn check-bytes! [expected actual stage]
  (let [expected-hash (digest expected)
        actual-hash (digest actual)]
    (when-not (and (= (alength ^bytes expected) (alength ^bytes actual)) (= expected-hash actual-hash))
      (throw (ex-info (format "%s: content differs (%d bytes, SHA-256 %s; expected %d bytes, SHA-256 %s)"
                              stage (alength ^bytes actual) actual-hash (alength ^bytes expected) expected-hash)
                      {:stage stage})))))

(defn restored-id [node-id response]
  (let [prefix (str "Node " node-id " successfully restored as ")
        value (str/trim (text response))]
    (when-not (str/starts-with? value prefix)
      (throw (ex-info (str "Unexpected restore response: " value) {})))
    (let [result (subs value (count prefix))]
      (UUID/fromString result)
      result)))

(defn prepare! []
  (wait-for! "Alfresco" #(request! alfresco :get (str core "/nodes/-root-")))
  (let [vault (env "SECRETS_URL" "http://secrets:8200")
        secret (byte-array 32)]
    (wait-for! "Vault" #(request! vault :get "/v1/sys/health" {:auth? false}))
    (.nextBytes (SecureRandom.) secret)
    (request! vault :post "/v1/secret/data/AlfrescoNodeVaultSmoke"
              {:data {:data {"anv.secret" (hex secret)}} :auth? false :headers {"X-Vault-Token" "root"}}))
  (let [s3 (env "S3_URL" "http://motoserver:5000")]
    (wait-for! "Moto" #(request! s3 :get "/" {:auth? false}))
    (doseq [case ["s3-plain" "s3-encrypted"]]
      (request! s3 :put (str "/anv-smoke-" case)
                {:auth? false :expected #{200 409} :headers {"Content-Type" "application/xml"}
                 :data (str "<CreateBucketConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
                            "<LocationConstraint>eu-central-1</LocationConstraint></CreateBucketConfiguration>")})))
  (log! "Smoke secrets and buckets initialized"))

(defn run-file! [case base folder-id node-ids [filename mime-type payload]]
  (let [node (read-json (request! alfresco :post (str core "/nodes/" folder-id "/children")
                                  {:data {:name filename :nodeType "cm:content"} :expected #{201}}))
        node-id (get-in node [:entry :id])
        source-path (str core "/nodes/" node-id)
        vault-path (str api "/nodes/" node-id)]
    (swap! node-ids conj node-id)
    (request! alfresco :put (str source-path "/content") {:data payload :headers {"Content-Type" mime-type}})
    (check-bytes! payload (request! alfresco :get (str source-path "/content")) "source")
    (request! base :post (str vault-path "/archive"))
    (request! alfresco :get source-path {:expected #{404}})
    (let [archived (:entry (read-json (request! base :get vault-path)))]
      (when-not (and (= node-id (:id archived)) (= filename (:name archived)))
        (throw (ex-info (str "Archived metadata differs for " filename) {}))))
    (check-bytes! payload (request! base :get (str vault-path "/content")) "vault download")
    (let [restore-path (str vault-path "/restore?restorePermissions=true")
          target-id (restored-id node-id (request! base :post restore-path))]
      (check-bytes! payload (request! alfresco :get (str core "/nodes/" target-id "/content")) "restored content")
      (when-not (= target-id (restored-id node-id (request! base :post restore-path)))
        (throw (ex-info (str "Restore retry created a different node for " filename) {})))
      (log! (format "PASS %s: %s, %d bytes, SHA-256 %s" case filename (alength ^bytes payload) (digest payload)))
      {:name filename :size (alength ^bytes payload) :sha256 (digest payload)
       :archivedId node-id :restoredId target-id})))

(defn cleanup! [base folder-id node-ids]
  (let [operations (cond-> (mapv (fn [id] #(request! base :delete (str api "/nodes/" id) {:expected #{200 404}})) node-ids)
                     folder-id (conj #(request! alfresco :delete (str core "/nodes/" folder-id "?permanent=true")
                                                {:expected #{204 404}})))]
    (into [] (keep (fn [operation] (try (operation) nil (catch Exception e (ex-message e))))) operations)))

(defn run-case! [case fixtures]
  (let [base (str "http://app-" case ":8086")
        folder-id (atom nil)
        node-ids (atom [])
        files (atom [])
        outcome (try
                  (wait-for! case #(request! base :get (str api "/search?page=0&size=1")))
                  (request! base :get (str api "/search") {:auth? false :expected #{401}})
                  (reset! folder-id (get-in (read-json (request! alfresco :post (str core "/nodes/-root-/children")
                                                               {:data {:name (str "anv-smoke-" case "-" (UUID/randomUUID))
                                                                       :nodeType "cm:folder"}
                                                                :expected #{201}})) [:entry :id]))
                  (doseq [fixture fixtures]
                    (swap! files conj (run-file! case base @folder-id node-ids fixture)))
                  (let [children (read-json (request! alfresco :get (str core "/nodes/" @folder-id "/children?maxItems=100")))]
                    (when-not (= (count fixtures) (get-in children [:list :pagination :count]))
                      (throw (ex-info "Unexpected number of restored nodes; possible duplicates" {}))))
                  {:status "PASSED"}
                  (catch Exception e
                    (log! (str "FAIL " case ": " (ex-message e)))
                    {:status "FAILED" :error (ex-message e)}))
        cleanup-errors (cleanup! base @folder-id @node-ids)]
    (cond-> (merge {:case case :files @files :cleanupErrors cleanup-errors} outcome)
      (seq cleanup-errors) (assoc :status "FAILED"))))

(defn fixtures []
  (let [size-mb (parse-long (env "SMOKE_LARGE_FILE_MB" "8"))]
    (when-not (and size-mb (<= 1 size-mb 256))
      (throw (ex-info "SMOKE_LARGE_FILE_MB must be between 1 and 256" {})))
    (let [data (byte-array (* size-mb 1024 1024))]
      (.nextBytes (Random. 0) data)
      [["empty.bin" "application/octet-stream" (byte-array 0)]
       ["documento-città.pdf" "application/pdf" (Files/readAllBytes (.toPath (io/file "/smoke/sample.pdf")))]
       ["large.bin" "application/octet-stream" data]])))

(defn -main [& args]
  (if (= ["prepare"] (vec args))
    (do (prepare!) 0)
    (let [payloads (fixtures)
          results (mapv #(run-case! % payloads) cases)
          report {:passed (every? #(= "PASSED" (:status %)) results) :results results}
          report-path (io/file (env "SMOKE_REPORT" "/reports/report.json"))
          output (str (json/generate-string report {:pretty true}) "\n")]
      (io/make-parents report-path)
      (spit report-path output :encoding "UTF-8")
      (log! output)
      (if (:passed report) 0 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (System/exit (apply -main *command-line-args*)))
