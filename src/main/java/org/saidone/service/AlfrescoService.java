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
import org.alfresco.core.model.NodeBodyUpdate;
import org.alfresco.search.handler.SearchApi;
import org.alfresco.search.model.RequestPagination;
import org.alfresco.search.model.RequestQuery;
import org.alfresco.search.model.ResultSetPaging;
import org.alfresco.search.model.SearchRequest;
import org.saidone.component.BaseComponent;
import org.saidone.config.AlfrescoServiceConfig;
import org.saidone.exception.ApiExceptionError;
import org.saidone.model.SystemSearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
            log.debug("Guest Home => {}", guestHomeNode.getId());
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
        log.trace("Available memory => {} bytes", availableMemory);
        val dynamicBufferSize = (int) Math.min((long) maxChunkSizeKib * 1024, availableMemory / (2L * parallelism));
        log.trace("Dynamic buffer size => {}", dynamicBufferSize);

        var url = new URL(String.format("%s%s/nodes/%s/content", contentServiceUrl, contentServicePath, nodeId));
        var conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty(HttpHeaderNames.AUTHORIZATION.toString(), basicAuth);

        @Cleanup var in = conn.getInputStream();

        var tempFile = File.createTempFile("alfresco-content-", ".tmp");

        @Cleanup var out = new FileOutputStream(tempFile);
        byte[] buffer = new byte[dynamicBufferSize];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        return tempFile;
    }

    public void addAspects(String nodeId, List<String> additionalAspectNames) {
        var aspectNames = Objects.requireNonNull(nodesApi.getNode(nodeId, null, null, null).getBody()).getEntry().getAspectNames();
        aspectNames.addAll(additionalAspectNames);
        var nodeBodyUpdate = new NodeBodyUpdate();
        nodeBodyUpdate.setAspectNames(aspectNames);
        log.debug("Adding aspects => {}", additionalAspectNames);
        nodesApi.updateNode(nodeId, nodeBodyUpdate, null, null);
    }

    public void removeAspects(String nodeId, List<String> aspectsToRemove) {
        var aspectNames = Objects.requireNonNull(nodesApi.getNode(nodeId, null, null, null).getBody()).getEntry().getAspectNames();
        aspectNames.removeAll(aspectsToRemove);
        var nodeBodyUpdate = new NodeBodyUpdate();
        nodeBodyUpdate.setAspectNames(aspectNames);
        log.debug("Removing aspects => {}", aspectsToRemove);
        nodesApi.updateNode(nodeId, nodeBodyUpdate, null, null);
    }

    public void deleteNode(String nodeId) {
        nodesApi.deleteNode(nodeId, config.isPermanentlyDeleteNodes());
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
        var searchRequest = new SystemSearchRequest();
        searchRequest.setQuery(query);
        searchRequest.setSkipCount(0);
        searchRequest.setMaxItems(config.getSearchBatchSize());
        ResultSetPaging resultSetPaging;
        var documentsProcessed = new AtomicInteger();
        do {
            log.debug("skipCount => {}", searchRequest.getSkipCount());
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
        log.info("Documents archived => {}", documentsProcessed.get());
    }

    private ResultSetPaging search(SystemSearchRequest systemSearchRequest) {
        var searchRequest = new SearchRequest();
        var requestQuery = new RequestQuery();
        requestQuery.setLanguage(RequestQuery.LanguageEnum.AFTS);
        requestQuery.setQuery(systemSearchRequest.getQuery());
        var paging = new RequestPagination();
        paging.setMaxItems(systemSearchRequest.getMaxItems());
        paging.setSkipCount(systemSearchRequest.getSkipCount());
        searchRequest.setQuery(requestQuery);
        searchRequest.setPaging(paging);
        return searchApi.search(searchRequest).getBody();
    }

    @SneakyThrows
    public static String getErrorKey(FeignException e) {
        var objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true);
        var charset = StandardCharsets.UTF_8;
        var body = charset.decode(e.responseBody().orElse(ByteBuffer.allocate(0))).toString();
        var apiExceptionError = objectMapper.readValue(body, ApiExceptionError.class);
        return apiExceptionError.getErrorKey();
    }

}