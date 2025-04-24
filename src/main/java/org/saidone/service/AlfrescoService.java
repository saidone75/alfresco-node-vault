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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    public static Node guestHome;
    private static int parallelism;

    @Value("${application.service.alfresco.max-chunk-size-mib}")
    private int maxChunkSizeMib;

    @PostConstruct
    @Override
    public void init() {
        super.init();
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
        val dynamicChunkSize = (int) Math.min((long) maxChunkSizeMib * 1024 * 1024, availableMemory / (2L * parallelism));
        log.trace("Dynamic chunk size => {}", dynamicChunkSize);
        var offset = 0L;
        var tempFile = File.createTempFile("alfresco-content-", ".tmp");
        try {
            while (true) {
                var range = String.format("bytes=%d-%d", offset, offset + dynamicChunkSize - 1);
                log.trace("Range => {}", range);
                var nodeContent = nodesApi.getNodeContent(nodeId, false, null, range).getBody();
                if (nodeContent == null) {
                    if (offset == 0) {
                        log.warn("Content not found for node => {}", nodeId);
                        Files.deleteIfExists(tempFile.toPath());
                        return null;
                    }
                    break;
                }
                try (var inputStream = nodeContent.getInputStream();
                     var outputStream = new FileOutputStream(tempFile, true)) {
                    var chunk = inputStream.readAllBytes();
                    log.trace("Read {} bytes", chunk.length);
                    outputStream.write(chunk);
                    if (chunk.length < dynamicChunkSize) {
                        break;
                    }
                    offset += chunk.length;
                }
            }
            return tempFile;
        } catch (Exception e) {
            log.error("Error retrieving content of node => {}", nodeId, e);
            Files.deleteIfExists(tempFile.toPath());
            throw e;
        }
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