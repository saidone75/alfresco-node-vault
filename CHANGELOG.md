# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added
- Optional Alfresco node locking during archive, verification and deletion with `VAULT_ALFRESCO_USE_LOCK=true`; requires a dedicated Alfresco account and does not provide a cross-system transaction.
- ANV v1 encrypted payloads with a versioned, authenticated binary header recording cipher, KDF parameters, logical key reference, Vault key version, salt and nonce; content and metadata remain readable after changing write settings.
- Prometheus counters for archive, restore and delete outcomes, duration histograms and successfully archived content bytes, with a dedicated Grafana dashboard row; disable these metrics with `VAULT_OPERATION_METRICS_ENABLED=false`. Internal restore steps count only as part of delete, and archive retries do not increase byte counts.
- Selectable Zulu, GraalVM Community or IBM Semeru/OpenJ9 Docker JVM runtimes via `VAULT_JVM_RUNTIME` in `docker/.env`.
- Hourly, configurable node lock cleanup removes only `node:` locks expired for at least one hour, coordinated across instances through a separate job lock.
- MongoDB-backed ShedLock coordination for node archiving, restore, deletion, key rotation, notarization and notarization checks, plus shared locks for archiving jobs, notarization jobs and manual or scheduled integrity sweeps. Node and job leases default to two minutes, with periodic renewal and configurable durations.

### Changed
- S3 uploads stream staged files instead of buffering entire contents in memory; content encryption runs during staging and uses a single temporary file.
- Temporary files use a dedicated directory cleaned at startup; each application instance must have its own directory.
- Notarization and bulk key rotation retrieve node IDs in pages instead of loading all matching nodes at once.
- Ozone client write buffers are configurable through `OZONE_CLIENT_BUFFER_SIZE`, `OZONE_CLIENT_BUFFER_FLUSH_SIZE` and `OZONE_CLIENT_BUFFER_MAX_SIZE`, defaulting to 1, 2 and 4 MB.
- Vault secrets are read directly without submitting blocking calls to the common pool and immediately waiting for them; retrieval failures preserve their causes in `VaultException`.
- Content services consistently use property-based backend selection; the encrypted metadata repository is explicitly marked as primary, and the path filter's API dependency is final.
- **Breaking:** unversioned encrypted payloads are no longer supported. Export with the previous application before upgrading and reimport, or recreate disposable data. ANV v1 requires 12-byte nonces and bounded KDF costs.
- Archiving uses a configurable fixed delay (ten seconds by default) in dev and Docker profiles; other profiles retain cron scheduling. Both modes share the distributed job lock.
- Concurrent synchronous operations on the same node return `409 Conflict` across cooperating instances. Background jobs skip busy nodes; bulk key rotation logs busy nodes and continues. Integrity checks on busy nodes count as check errors without marking content as corrupted.
- Archiving always checks that the size of the content received matches the size reported by Alfresco, rolling back on mismatch.
- Before deleting on Alfresco a node already in the vault, its Alfresco content is always compared with the checksum recorded in the vault, regardless of `VAULT_DOUBLE_CHECK`.

### Fixed
- Content restore closes source streams even when connection setup fails, closes HTTP response streams and disconnects connections after uploads.
- Event deduplication atomically checks and updates node expiry; cleanup cannot remove entries renewed concurrently.
- GridFS content hashes are computed by streaming original content bytes, including decryption when required; archive verification uses the checksum algorithm recorded with the content.
- Integrity sweeps distinguish evidence of corrupted content from unavailable backends and other check errors, preserving previous verdicts on operational failures and skipping nodes deleted during a sweep.
- Content download streams are released through the reactive response lifecycle; Alfresco content connections have configurable connect and read timeouts.
- Vault metadata is saved only after optional archive double-check verification succeeds.
- Startup with both content and metadata encryption disabled no longer requires crypto beans.
- Development PostgreSQL 18 volume mount uses `/var/lib/postgresql`; Alfresco waits for the database healthcheck before starting.
- Restore path creation resolves existing folders directly after a name conflict, without relying on paginated child listings. Other Alfresco errors are propagated, and files with conflicting names are rejected.

## [0.9.2] - 2026-10-20

### Added
- Native Apache Ozone content storage, with optional encryption, checksum verification and content metadata, alongside S3 and GridFS.
- Ozone development and smoke-test Docker stacks, including healthcheck-based initialization, persistent data volumes and the Recon web UI.
- Explicit Maven profiles (`s3`, `gridfs`, `ozone`) for selecting the integration-test storage backend.
- Optional retention of integration-test fixtures with `-Dtest.cleanup.enabled=false`; cleanup remains enabled by default.

### Changed
- Unified smoke tests across S3, GridFS and Ozone, with backend selection in the Bash and Windows launchers.
- Development launcher supports storage backend selection and stops all containers in its Compose project regardless of the previously selected backend, preserving data volumes.
- Increased Alfresco and Share memory limits, clarified Grafana garbage-collection metrics and suppressed repetitive JMS header-mapping info logs.
- Updated AWS SDK to 2.56.0, Nimbus JOSE JWT to 10.10, ASM to 9.11.1 and OkHttp JVM to 5.5.0; updated Versions Maven Plugin to 2.23.0 and CodeQL Action to 4.39.0.

### Fixed
- Versioned JAR naming in Maven, Docker and distribution scripts; distribution builds now run `clean package` and stop when Maven fails.
- Smoke-test dependency startup when selecting an individual storage backend.

## [0.9.1] - 2026-10-18

### Added
- Isolated end-to-end smoke tests written in Clojure with Babashka for S3 and GridFS with and without encryption, checking archive/download/restore content hashes, restore retries and fixture cleanup. Bash and Windows batch launchers build and start the Docker environment and run the suite.
- Advanced search API (`POST /api/v1/vault/advanced-search`) on node metadata: name with `*` and `?` wildcards, node type, creation and modification dates, mimetype, size, aspects and custom properties, combining values and conditions with `ANY`/`ALL`. Available only when metadata encryption is disabled.
- Delete node API (`DELETE /api/v1/vault/nodes/{nodeId}`), disabled by default (`${VAULT_DELETE_NODE_API_ENABLED:false}`).
- Integrity sweep, scheduled or on demand, with hybrid mode and configurable coverage, batching, persistence of corrupted-node lists and Grafana dashboard.
- Separate flags for content and metadata encryption (`${VAULT_ENCRYPT_CONTENT}` and `${VAULT_ENCRYPT_METADATA}`); non-encrypted metadata is stored as a BSON document.
- Caching of successful API authorizations (`${VAULT_AUTH_CACHE_TTL:60s}`).
- Loki and Promtail in the Docker setup for log aggregation.
- REST API documentation covering authentication, proxy routes and response conventions, and documented limitations when changing encryption settings on an existing vault.

### Changed
- **Breaking:** REST APIs are now versioned under `/api/v1` (`/api/v1/vault` and `/api/v1/audit`).
- Reviewed API error responses: invalid parameters, request bodies and pagination return `400` instead of `500`; checking the notarization of a node that is not notarized, or whose hash does not match, returns `409` instead of `500`; all plain-text responses use the `text/plain` content type.
- Completed node restores reuse the existing Alfresco node while it is still available; concurrent restores of the same node are rejected within one application instance.
- `ResourceFileUtils` now opens filesystem files and classpath resources as streams instead of extracting resources to temporary files. Callers are responsible for closing the returned stream.
- The content key version is recorded on archived nodes, so key rotation also finds nodes with plain metadata.
- Blocking operations no longer run on the Netty event loop.
- Upgraded to Spring Boot 4.1.1 and Spring Cloud 2025.1.3, Alfresco Java starters 7.3.3, AWS SDK 2.55.5, web3j 6.0.0, SpringDoc WebFlux UI 3.1.1, Bouncy Castle 1.86 and Datafaker 2.7.0.
- Fixed Maven dependency convergence by importing the AWS SDK BOM and pinning transitive versions.
- Docker: Java 25 runtime (Azul Zulu), Moto instead of LocalStack, modular Compose files; the vault image build copies only the build inputs.
- Maven artifact renamed to `anv`; distribution scripts and Docker builds accept versioned JAR names.
- CI: Maven dependency submission replaces OWASP Dependency-Check.

### Fixed
- Reflected XSS in plain-text API responses.
- S3 content is decrypted only when marked as encrypted, so content stored before enabling encryption can still be read.
- A failed archiving rollback no longer deletes valid vault data.
- GridFS lookup key and duplicate files per node; the content type is stored under the `ct` metadata key.
- S3 user metadata is persisted on content upload.
- S3 upload checksums are computed once and reused for logging and metadata, preventing the stored checksum from being replaced by the hash of empty content.
- Failed restores attempt to permanently remove only the newly created Alfresco node if content upload or saving the restored node ID fails, preserving the original error. Process interruptions, lost creation responses or cleanup failures may still require manual reconciliation.
- S3 upload temporary files are created in private directories with restrictive POSIX permissions or Windows ACLs and cleaned up after use, including on failure. Creation fails if neither protection is supported.
- Test file extension extraction no longer uses regular expressions with excessive backtracking.
- Invalid API credentials return `401` instead of `500`.
- Startup with notarization disabled.
- Notarization waits for transactions to be successfully mined.
- Checking whether a node is on the vault no longer decrypts its metadata.

## [0.9.0] - 2026-01-12

### Added
- New `delete-on-alfresco` flag (`${VAULT_DELETE_ON_ALFRESCO:true}`) in configuration.
- Archive date search API (`GET /api/vault/search`) with pagination and sorting; searches default to the last 24 hours when no date range is supplied. Encrypted node metadata is decrypted when returning results.
- Encryption key rotation APIs for a single node (`POST /api/vault/nodes/{nodeId}/update-key`) or all nodes using a selected key version (`POST /api/vault/nodes/update-keys?keyVersion=...`). Bulk rotation runs asynchronously.
- Encryption key version metadata on archived nodes and stored binaries, used to select nodes for re-encryption.
- Automatic initialization of the `alf_node` collection and its archive date index.

### Changed
- **Breaking:** audit API pagination parameters changed from `maxItems` and `skipCount` to zero-based `page` and `size`.
- Key update operations use `POST` instead of `GET`, and asynchronous controller operations use a configured task executor.
- Centralized REST exception handling with plain-text error responses.
- Audit entries use a dedicated model and record request/response metadata without serializing bodies.
- Updated Spring Boot parent to 3.5.9 and aligned with Spring Cloud 2025.0.1 for the latest platform fixes.
- Refreshed AWS SDK dependencies (S3, transfer manager, and SDK core) to 2.41.1.
- Bumped supporting libraries including Commons IO 2.21.0 and SpringDoc WebFlux UI 2.8.14.
- Updated build and test tooling (Datafaker 2.5.3, JaCoCo Maven Plugin 0.8.14).
- CI now uploads build artifacts using `actions/upload-artifact@v5`.

### Fixed
- Archive date index creation uses an explicit index name and the supported MongoDB index API.
- Collection casting utilities handle null inputs and validate container and element types.

## [0.0.3] - 2025-07-21

### Added
- Introduced node notarization: records content checksums on the blockchain to ensure tamper-proof integrity.
- On-demand notarization and verification APIs (`POST` and `GET /api/vault/nodes/{nodeId}/notarize`); notarization requests run asynchronously, and already notarized nodes return `409`.
- Configurable scheduled notarization, with a default interval of five minutes.
- Configurable audit retention (`${AUDIT_TTL_DAYS:60}`) through a MongoDB TTL policy.
- Ganache in the development Docker environment for testing Ethereum notarization.
- Separate `NodeContentInfo` and `NodeContentStream` descriptors for stored content metadata and streamed binary retrieval, including checksum and encryption information.

### Changed
- Refactored content services to use `NodeContentInfo`.
- Ethereum transactions use the configured private key and chain ID, with dynamically estimated gas price and gas limit.
- Stored node and audit metadata use compact MongoDB field names.
- Windows distribution build script updated.
- Updated dependencies (AWS SDK, Datafaker, Web3j, etc.).
- Added numerous Javadoc comments and documentation improvements.

### Fixed
- Corrected encryption flag parsing in `GridFsContentService`.
- Archive failures trigger a dedicated rollback routine, with cleanup failures logged separately.
- Notarization failures, missing transactions and hash mismatches are reported through `NotarizationException`.
- Fixed typos and Javadoc placement issues.

## [0.0.2] - 2025-07-07

### Added
- **Audit trail**: incoming requests and outgoing responses are now audited and stored in a MongoDB time series collection. Entries are accessible via the new `/api/audit` endpoint.
- Audit queries support filtering by entry type and timestamp range, with pagination.
- **S3 storage**: binaries can be stored on Amazon S3 or compatible services as an alternative to GridFS.
- Plain and encrypted S3 repository implementations, with configurable credentials and a custom endpoint for local or compatible storage services.
- Automatic S3 bucket cleanup in the integration test cleanup service.

### Changed
- Introduced a `ContentService` abstraction with implementations for GridFS and S3.
- Added LocalStack configuration and Docker setup to easily test S3 integration.
- Improved authentication by centralizing logic in `AuthenticationService`.
- Enhanced error handling in REST controllers.
- Updated `vault.sh` script with an optional `novault` start parameter and split monitoring stack.
- Log rotation allows larger files and longer retention, with a total archive size cap.
- Bumped dependencies (Spring Boot 3.5.3, Spring Cloud 2025.0.0, AWS SDK 2.31.x, etc.).
- Added numerous Javadoc comments and updated documentation.

### Fixed
- S3 uploads use the configured client, and content retrieval goes through the selected repository implementation.
- Encryption and storage configuration select the appropriate service and repository implementations.
- Event-based archiving filters content nodes by the added archive aspect and uses the configured duplicate-processing threshold.
- Expired entries are removed correctly from the recently processed node filter.
- Basic authentication credentials are decoded as UTF-8 and authorization headers are validated centrally.
- Classpath resource extraction preserves the original file extension.

## [0.0.1] - 2025-06-02

### Added
- Archive Alfresco nodes either on-demand or via scheduled jobs.
- Optional strong encryption for both content and metadata.
- AES-GCM and ChaCha20-Poly1305 encryption implementations, with PBKDF2, HKDF or Argon2 key derivation.
- Versioned Vault KV secrets, with the key version, salt and IV/nonce included in encrypted payloads.
- Store documents in MongoDB with GridFS.
- Transparent access via REST proxy, even after deletion from Alfresco.
- Supports automatic document restoration if needed.
- Restoration can reinstate permissions and recreate missing parent paths.
- Optional checksum verification before removing the source document from Alfresco.
- HTTP Basic authentication for vault APIs using Alfresco administrator credentials; separate authentication for actuator endpoints.
- OpenAPI documentation and Swagger UI at `/swagger`.
- Monitoring with Prometheus.
- Grafana dashboards for application monitoring.
- Vault integration for secure secrets storage.
- Docker setup for easy development.
