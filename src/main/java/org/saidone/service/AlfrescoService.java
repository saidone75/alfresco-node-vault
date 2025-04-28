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
import io.netty.handler.codec.http.HttpHeaderNames;
import jakarta.annotation.PostConstruct;
import lombok.Cleanup;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.core.handler.NodesApi;
import org.alfresco.core.model.Node;
import org.alfresco.core.model.NodeBodyCreate;
import org.alfresco.core.model.NodeBodyUpdate;
import org.alfresco.core.model.PermissionsBody;
import org.alfresco.search.handler.SearchApi;
import org.alfresco.search.model.RequestPagination;
import org.alfresco.search.model.RequestQuery;
import org.alfresco.search.model.ResultSetPaging;
import org.alfresco.search.model.SearchRequest;
import org.saidone.component.BaseComponent;
import org.saidone.config.AlfrescoServiceConfig;
import org.saidone.exception.ApiExceptionError;
import org.saidone.model.NodeContent;
import org.saidone.model.SystemSearchRequest;
import org.saidone.model.alfresco.AnvContentModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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

    private static String basicAuth;
    public static Node guestHome;
    private static int parallelism;

    @Value("${application.service.alfresco.max-chunk-size-kib}")
    private int maxChunkSizeKib;

    @PostConstruct
    @Override
    public void init() {
        super.init();
        basicAuth = String.format("Basic %s", Base64.getEncoder().encodeToString((String.format("%s:%s", userName, password)).getBytes(StandardCharsets.UTF_8)));
        guestHome = getGuestHome();
        parallelism = ForkJoinPool.commonPool().getParallelism();
    }

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

    public Node getNode(String nodeId) {
        return Objects.requireNonNull(nodesApi.getNode(nodeId, config.getInclude(), null, null).getBody()).getEntry();
    }

    @SneakyThrows
    public File getNodeContent(String nodeId) {
        val availableMemory = Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory() + Runtime.getRuntime().freeMemory();
        log.trace("Available memory: {} bytes", availableMemory);
        val dynamicBufferSize = (int) Math.min((long) maxChunkSizeKib * 1024, availableMemory / (2L * parallelism));
        log.trace("Dynamic buffer size: {}", dynamicBufferSize);

        // workaround for getting a true stream instead of nodesApi.getNodeContent()
        val url = URI.create(String.format("%s%s/nodes/%s/content", contentServiceUrl, contentServicePath, nodeId)).toURL();
        val conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty(HttpHeaderNames.AUTHORIZATION.toString(), basicAuth);

        @Cleanup val in = conn.getInputStream();
        val tempFile = File.createTempFile("alfresco-content-", ".tmp");
        @Cleanup val out = new FileOutputStream(tempFile);

        val buffer = new byte[dynamicBufferSize];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        return tempFile;
    }

    public void addAspects(String nodeId, List<String> additionalAspectNames) {
        val aspectNames = Objects.requireNonNull(nodesApi.getNode(nodeId, null, null, null).getBody()).getEntry().getAspectNames();
        aspectNames.addAll(additionalAspectNames);
        val nodeBodyUpdate = new NodeBodyUpdate();
        nodeBodyUpdate.setAspectNames(aspectNames);
        log.debug("Adding aspects: {}", additionalAspectNames);
        nodesApi.updateNode(nodeId, nodeBodyUpdate, null, null);
    }

    public void removeAspects(String nodeId, List<String> aspectsToRemove) {
        val aspectNames = Objects.requireNonNull(nodesApi.getNode(nodeId, null, null, null).getBody()).getEntry().getAspectNames();
        aspectNames.removeAll(aspectsToRemove);
        val nodeBodyUpdate = new NodeBodyUpdate();
        nodeBodyUpdate.setAspectNames(aspectNames);
        log.debug("Removing aspects: {}", aspectsToRemove);
        nodesApi.updateNode(nodeId, nodeBodyUpdate, null, null);
    }

    public void deleteNode(String nodeId) {
        nodesApi.deleteNode(nodeId, config.isPermanentlyDeleteNodes());
    }

    @SneakyThrows
    public String restoreNode(Node node, boolean restorePermissions) {
        val nodeBodyCreate = new NodeBodyCreate();
        nodeBodyCreate.setName(node.getName());
        nodeBodyCreate.setNodeType(node.getNodeType());
        val aspectNames = node.getAspectNames();
        aspectNames.remove(AnvContentModel.ASP_ARCHIVE);
        nodeBodyCreate.setAspectNames(aspectNames);
        nodeBodyCreate.setProperties(node.getProperties());
        if (restorePermissions) {
            val permissionBody = new PermissionsBody();
            permissionBody.setIsInheritanceEnabled(node.getPermissions().isIsInheritanceEnabled());
            permissionBody.setLocallySet(node.getPermissions().getLocallySet());
            nodeBodyCreate.setPermissions(permissionBody);
        }
        nodeBodyCreate.setDefinition(node.getDefinition());
        return Objects.requireNonNull(nodesApi.createNode(node.getParentId(), nodeBodyCreate, true, null, null, null, null).getBody()).getEntry().getId();
    }

    @SneakyThrows
    public void restoreNodeContent(String nodeId, NodeContent nodeContent) {
        log.warn("This method will try to load the entire node content in memory");
        nodesApi.updateNodeContent(nodeId, nodeContent.getContentStream().readAllBytes(), null, null, null, null, null);
    }

    @SneakyThrows
    public void searchAndProcess(String query, Consumer<String> nodeProcessor) {
        searchAndProcess(query, null, nodeProcessor);
    }

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
        log.info("Documents archived: {}", documentsProcessed.get());
    }

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