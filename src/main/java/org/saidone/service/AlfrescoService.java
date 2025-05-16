/*
 *  Alfresco Node Vault - archive today, accelerate tomorrow
 *  Copyright (C) 2025 Saidone
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.saidone.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.core.handler.NodesApi;
import org.alfresco.core.model.*;
import org.alfresco.search.handler.SearchApi;
import org.alfresco.search.model.RequestPagination;
import org.alfresco.search.model.RequestQuery;
import org.alfresco.search.model.ResultSetPaging;
import org.alfresco.search.model.SearchRequest;
import org.saidone.component.BaseComponent;
import org.saidone.config.AlfrescoServiceConfig;
import org.saidone.exception.ApiExceptionError;
import org.saidone.exception.VaultException;
import org.saidone.misc.AnvDigestInputStream;
import org.saidone.misc.ProgressTrackingOutputStream;
import org.saidone.model.NodeContent;
import org.saidone.model.SystemSearchRequest;
import org.saidone.model.alfresco.AnvContentModel;
import org.saidone.utils.CastUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Service class for interacting with Alfresco repository nodes and content.
 * Provides methods to retrieve, update, delete, restore nodes and their content,
 * as well as search and process nodes in Alfresco.
 * <p>
 * Uses Alfresco REST APIs via Feign clients and WebClient for content operations.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlfrescoService extends BaseComponent {

    private final AlfrescoServiceConfig config;
    private final NodesApi nodesApi;
    private final SearchApi searchApi;

    @Value("${content.service.url}")
    private String contentServiceUrl;
    @Value("${content.service.path}")
    private String contentServicePath;
    @Value("${content.service.security.basicAuth.username}")
    private String userName;
    @Value("${content.service.security.basicAuth.password}")
    private String password;

    @Value("${application.service.alfresco.chunk-size}")
    private int chunkSize;

    private static String basicAuth;
    public static Node guestHome;

    /**
     * Initializes the service, sets up basic authentication header,
     * retrieves the "Guest Home" node, and configures the WebClient.
     */
    @PostConstruct
    @Override
    public void init() {
        super.init();
        basicAuth = String.format("Basic %s", Base64.getEncoder().encodeToString((String.format("%s:%s", userName, password)).getBytes(StandardCharsets.UTF_8)));
        guestHome = getGuestHome();
    }

    /**
     * Retrieves the "Guest Home" node from Alfresco repository.
     *
     * @return the Guest Home {@link Node} or null if not found
     */
    private Node getGuestHome() {
        var guestHomeNode = (Node) null;
        try {
            guestHomeNode = Objects.requireNonNull(nodesApi.getNode("-root-", null, "/Guest Home", null).getBody()).getEntry();
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
        if (guestHomeNode != null) {
            log.debug("Guest Home: {}", guestHomeNode.getId());
        } else {
            log.warn("Guest Home not found");
        }
        return guestHomeNode;
    }

    /**
     * Retrieves a node by its identifier.
     *
     * @param nodeId the identifier of the node to retrieve
     * @return the {@link Node} object
     * @throws VaultException if the node cannot be retrieved
     */
    public Node getNode(String nodeId) {
        return Objects.requireNonNull(nodesApi.getNode(nodeId, config.getInclude(), null, null).getBody()).getEntry();
    }

    /**
     * Retrieves the content of a node as an InputStream from the content service using the specified node identifier.
     * <p>
     * This method constructs the URL for the node content endpoint, opens a connection,
     * sets the required authorization header, and returns the response stream.
     *
     * @param nodeId the unique identifier of the node whose content is to be retrieved
     * @return an InputStream containing the node's content
     * @throws RuntimeException if an I/O error occurs while opening the connection or obtaining the content stream
     */
    @SneakyThrows
    public InputStream getNodeContent(String nodeId) {
        val url = URI.create(String.format("%s%s/nodes/%s/content", contentServiceUrl, contentServicePath, nodeId)).toURL();
        val conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty(HttpHeaders.AUTHORIZATION, basicAuth);
        return conn.getInputStream();
    }

    /**
     * Adds aspects to a node.
     *
     * @param nodeId                the identifier of the node to update
     * @param additionalAspectNames list of aspect names to add
     */
    public void addAspects(String nodeId, List<String> additionalAspectNames) {
        val aspectNames = Objects.requireNonNull(nodesApi.getNode(nodeId, null, null, null).getBody()).getEntry().getAspectNames();
        aspectNames.addAll(additionalAspectNames);
        val nodeBodyUpdate = new NodeBodyUpdate();
        nodeBodyUpdate.setAspectNames(aspectNames);
        log.debug("Adding aspects: {}", additionalAspectNames);
        nodesApi.updateNode(nodeId, nodeBodyUpdate, null, null);
    }

    /**
     * Removes specified aspects from a node.
     *
     * @param nodeId          the identifier of the node to update
     * @param aspectsToRemove list of aspect names to remove
     */
    public void removeAspects(String nodeId, List<String> aspectsToRemove) {
        val aspectNames = Objects.requireNonNull(nodesApi.getNode(nodeId, null, null, null).getBody()).getEntry().getAspectNames();
        aspectNames.removeAll(aspectsToRemove);
        val nodeBodyUpdate = new NodeBodyUpdate();
        nodeBodyUpdate.setAspectNames(aspectNames);
        log.debug("Removing aspects: {}", aspectsToRemove);
        nodesApi.updateNode(nodeId, nodeBodyUpdate, null, null);
    }

    /**
     * Deletes a node from the repository.
     *
     * @param nodeId the identifier of the node to delete
     */
    public void deleteNode(String nodeId) {
        nodesApi.deleteNode(nodeId, config.isPermanentlyDeleteNodes());
    }

    /**
     * Restores a previously archived or deleted node into the repository.
     * <p>
     * Recreates a node using provided node metadata, including name, type, aspects, properties,
     * and optionally restores the original permissions. Handles the reconstruction of the parent path
     * if the original parent no longer exists, by recreating the necessary path structure or resolving
     * the appropriate parent node. Removes archival aspects before restoration, maintains a reference
     * to the original node, and creates the node in the repository with restored settings.
     *
     * @param node               the node to be restored, containing all necessary metadata and path information
     * @param restorePermissions whether to reinstate original permission settings for the node
     * @return the identifier of the newly restored node in the repository
     * @throws RuntimeException if the operation fails due to API errors or missing parent structure
     */
    @SneakyThrows
    public synchronized String restoreNode(Node node, boolean restorePermissions) {
        val nodeBodyCreate = new NodeBodyCreate();
        nodeBodyCreate.setName(node.getName());
        nodeBodyCreate.setNodeType(node.getNodeType());
        val aspectNames = node.getAspectNames();
        aspectNames.remove(AnvContentModel.ASP_ARCHIVE);
        nodeBodyCreate.setAspectNames(aspectNames);
        val properties = CastUtils.castToMapOfStringObject(node.getProperties());
        properties.put(AnvContentModel.PROP_WAS, node.getId());
        nodeBodyCreate.setProperties(properties);
        if (restorePermissions) {
            val permissionBody = new PermissionsBody();
            permissionBody.setIsInheritanceEnabled(node.getPermissions().isIsInheritanceEnabled());
            permissionBody.setLocallySet(node.getPermissions().getLocallySet());
            nodeBodyCreate.setPermissions(permissionBody);
        }
        nodeBodyCreate.setDefinition(node.getDefinition());

        if (!nodeExists(node.getParentId())) {
            log.debug("Parent node {} does not exist anymore, checking path...", node.getParentId());
            val originalPathElements = node.getPath().getElements().stream().map(PathElement::getName).toList().subList(1, node.getPath().getElements().size());
            if (!pathExists(String.join("/", originalPathElements))) {
                log.debug("Path {} does not exist anymore, creating path structure...", String.join("/", originalPathElements));
                node.setParentId(createPathIfNotExists("-root-", originalPathElements));
            } else {
                log.debug("Path {} exists, getting parent node...", String.join("/", originalPathElements));
                node.setParentId(Objects.requireNonNull(nodesApi.getNode("-root-", null, String.join("/", originalPathElements), null).getBody()).getEntry().getId());
            }
        }

        return Objects.requireNonNull(nodesApi.createNode(node.getParentId(), nodeBodyCreate, true, null, null, null, null).getBody()).getEntry().getId();
    }

    /**
     * Restores the content of a node by uploading the provided content stream.
     *
     * @param nodeId      the identifier of the node to update content for
     * @param nodeContent the {@link NodeContent} containing the content stream and metadata
     */
    @SneakyThrows
    public void restoreNodeContent(String nodeId, NodeContent nodeContent) {
        val url = URI.create(String.format("%s%s/nodes/%s/content", contentServiceUrl, contentServicePath, nodeId)).toURL();
        val conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(HttpMethod.PUT.name());
        conn.setDoOutput(true);
        conn.setRequestProperty(HttpHeaders.AUTHORIZATION, basicAuth);
        conn.setRequestProperty(HttpHeaders.CONTENT_TYPE, nodeContent.getContentType());
        conn.setChunkedStreamingMode(chunkSize);

        try (val is = nodeContent.getContentStream();
             val os = new ProgressTrackingOutputStream(conn.getOutputStream(), nodeId, nodeContent.getLength())) {
            is.transferTo(os);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            log.debug("Upload completed successfully for node: {}", nodeId);
        } else {
            log.error("Failed to upload content for node {} with HTTP status: {}", nodeId, responseCode);
            throw new VaultException(String.format("Failed to upload content for node %s with HTTP status: %d", nodeId, responseCode));
        }
    }

    /**
     * Computes a cryptographic hash of the content for the specified node using the provided algorithm.
     * <p>
     * This method retrieves the node content from Alfresco, calculates the hash using the specified
     * algorithm via an {@link AnvDigestInputStream}, and returns the hash value as a hexadecimal string.
     * The node content is streamed directly through the hash computation to avoid loading the entire
     * content into memory.
     *
     * @param nodeId    the unique identifier of the node whose content will be hashed
     * @param algorithm the name of the hash algorithm to use (e.g., "MD5", "SHA-256")
     * @return a hexadecimal string representation of the computed hash
     * @throws RuntimeException if any I/O or algorithm-related exceptions occur during processing
     *                          (wrapped via {@code @SneakyThrows})
     */
    @SneakyThrows
    public String computeHash(String nodeId, String algorithm) {
        try (val alfrescoDigestInputStream = new AnvDigestInputStream(getNodeContent(nodeId), algorithm)) {
            alfrescoDigestInputStream.transferTo(OutputStream.nullOutputStream());
            return alfrescoDigestInputStream.getHash();
        }
    }

    /**
     * Executes a search query and processes each resulting node ID using the provided processor.
     * Processes all pages of results.
     *
     * @param query         the search query string
     * @param nodeProcessor a {@link Consumer} that processes node IDs
     */
    @SneakyThrows
    public void searchAndProcess(String query, Consumer<String> nodeProcessor) {
        searchAndProcess(query, null, nodeProcessor);
    }

    /**
     * Executes a search query and processes each resulting node ID using the provided processor,
     * limiting the number of pages processed.
     *
     * @param query         the search query string
     * @param pages         maximum number of pages to process; if null or less than 1, all pages are processed
     * @param nodeProcessor a {@link Consumer} that processes node IDs
     */
    @SneakyThrows
    public void searchAndProcess(String query, Integer pages, Consumer<String> nodeProcessor) {
        if (pages == null || pages < 1) {
            pages = Integer.MAX_VALUE;
        }
        val searchRequest = new SystemSearchRequest();
        searchRequest.setQuery(query);
        searchRequest.setSkipCount(0);
        searchRequest.setMaxItems(config.getSearchBatchSize());
        ResultSetPaging resultSetPaging;
        val documentsProcessed = new AtomicInteger();
        do {
            log.debug("Skip count: {}", searchRequest.getSkipCount());
            resultSetPaging = search(searchRequest);
            if (nodeProcessor != null) {
                resultSetPaging.getList().getEntries().parallelStream().forEach(e -> {
                    try {
                        nodeProcessor.accept(e.getEntry().getId());
                        documentsProcessed.getAndIncrement();
                    } catch (Exception ex) {
                        log.error(ex.getMessage(), ex);
                    }
                });
            }
            searchRequest.setSkipCount(searchRequest.getSkipCount() + config.getSearchBatchSize());
            pages--;
        } while (resultSetPaging.getList().getPagination().isHasMoreItems() && pages > 0);
        log.info("Documents processed: {}", documentsProcessed.get());
    }

    /**
     * Performs a search request using the Alfresco Search API.
     *
     * @param systemSearchRequest the search request parameters
     * @return the {@link ResultSetPaging} containing search results
     */
    private ResultSetPaging search(SystemSearchRequest systemSearchRequest) {
        val searchRequest = new SearchRequest();
        val requestQuery = new RequestQuery();
        requestQuery.setLanguage(RequestQuery.LanguageEnum.AFTS);
        requestQuery.setQuery(systemSearchRequest.getQuery());
        val paging = new RequestPagination();
        paging.setMaxItems(systemSearchRequest.getMaxItems());
        paging.setSkipCount(systemSearchRequest.getSkipCount());
        searchRequest.setQuery(requestQuery);
        searchRequest.setPaging(paging);
        return searchApi.search(searchRequest).getBody();
    }

    /**
     * Attempts to create a folder structure based on the provided path under the specified parent node.
     * For each element in the path, tries to create a folder with that name inside the current parent.
     * If the folder already exists, retrieves its ID and proceeds to the next path element.
     * If creation and lookup both fail, throws an exception.
     *
     * @param parentId the ID of the parent node under which the folder structure should be created
     * @param path     the ordered list of folder names representing the path to create
     * @return the ID of the final node corresponding to the last path element
     * @throws Exception if a folder cannot be created or found at any point in the path
     */
    @SneakyThrows
    public String createPathIfNotExists(String parentId, List<String> path) {
        for (var pathPart : path) {
            var nodeBodyCreate = new NodeBodyCreate();
            nodeBodyCreate.setName(pathPart);
            nodeBodyCreate.setNodeType("cm:folder");
            log.debug("Trying to create folder: {}", nodeBodyCreate.getName());
            try {
                var nodeEntry = nodesApi.createNode(
                        parentId,
                        nodeBodyCreate,
                        false,
                        false,
                        false,
                        null,
                        null
                );
                log.debug("Created folder: {}", Objects.requireNonNull(nodeEntry.getBody()).getEntry().getName());
                parentId = Objects.requireNonNull(nodeEntry.getBody()).getEntry().getId();
            } catch (FeignException ex) {
                log.info("{}, getting children", getErrorKey(ex));
                /* get children */
                var children = nodesApi.listNodeChildren(
                        parentId,
                        null,
                        null,
                        null,
                        "(isFolder=true)",
                        null,
                        null,
                        null,
                        null
                );
                var matchingChild = Objects.requireNonNull(children.getBody()).getList().getEntries().stream().filter(e -> e.getEntry().getName().equals(pathPart)).findFirst().orElse(null);
                if (matchingChild != null) {
                    log.info("Found matching child: {}", matchingChild.getEntry().getName());
                    parentId = matchingChild.getEntry().getId();
                } else {
                    var message = String.format("Got %s but a child named %s does not exist", getErrorKey(ex), pathPart);
                    log.error("{}", message);
                    throw new Exception(message);
                }
            }
        }
        return parentId;
    }

    /**
     * Checks whether a node with the specified ID exists.
     * <p>
     * Sends a request to retrieve the node with the given {@code nodeId}. If the node is found, the method returns {@code true}.
     * If the node does not exist, a {@link FeignException.NotFound} is caught and the method returns {@code false}.
     *
     * @param nodeId the ID of the node to check for existence
     * @return {@code true} if the node exists, {@code false} otherwise
     */
    public boolean nodeExists(String nodeId) {
        try {
            nodesApi.getNode(nodeId, null, null, null);
            return true;
        } catch (FeignException.NotFound e) {
            return false;
        }
    }

    /**
     * Checks if a node exists at the given path within the repository.
     * <p>
     * Attempts to retrieve the node using the provided path. If the node is found, the method returns {@code true}.
     * If the node does not exist and a {@link FeignException.NotFound} is thrown, the method returns {@code false}.
     *
     * @param path the repository path to check for existence
     * @return {@code true} if a node exists at the specified path; {@code false} otherwise
     */
    public boolean pathExists(String path) {
        try {
            nodesApi.getNode("-root-", null, path, null);
            return true;
        } catch (FeignException.NotFound e) {
            return false;
        }
    }

    /**
     * Extracts the error key from a FeignException response body.
     *
     * @param e the {@link FeignException} thrown by a Feign client
     * @return the error key string extracted from the response
     */
    @SneakyThrows
    public static String getErrorKey(FeignException e) {
        val objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true);
        val charset = StandardCharsets.UTF_8;
        val body = charset.decode(e.responseBody().orElse(ByteBuffer.allocate(0))).toString();
        val apiExceptionError = objectMapper.readValue(body, ApiExceptionError.class);
        return apiExceptionError.getErrorKey();
    }

}