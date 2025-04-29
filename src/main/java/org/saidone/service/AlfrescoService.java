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
import org.saidone.exception.VaultException;
import org.saidone.model.NodeContent;
import org.saidone.model.SystemSearchRequest;
import org.saidone.model.alfresco.AnvContentModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
    private static WebClient webClient;
    public static Node guestHome;

    @PostConstruct
    @Override
    public void init() {
        super.init();
        basicAuth = String.format("Basic %s", Base64.getEncoder().encodeToString((String.format("%s:%s", userName, password)).getBytes(StandardCharsets.UTF_8)));
        guestHome = getGuestHome();
        webClient = WebClient.builder()
                .baseUrl(String.format("%s%s", contentServiceUrl, contentServicePath))
                .build();
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

    public File getNodeContent(String nodeId) {
        try {
            val tempFilePath = Files.createTempFile("alfresco-content-", ".tmp");
            log.trace("Created temp file: {}", tempFilePath);
            // workaround for nodesApi.getNodeContent()
            val fileMono = webClient.get()
                    .uri(String.format("%s%s/nodes/%s/content", contentServiceUrl, contentServicePath, nodeId))
                    .header(HttpHeaders.AUTHORIZATION, basicAuth)
                    .retrieve()
                    .bodyToFlux(DataBuffer.class)
                    .publishOn(Schedulers.boundedElastic())
                    .transform(flux -> DataBufferUtils.write(flux, tempFilePath, StandardOpenOption.WRITE))
                    .doOnComplete(() -> log.trace("Content written on file: {}", tempFilePath))
                    .doOnError(e -> log.error("Error downloading node {}: {}", nodeId, e.getMessage()))
                    .then(Mono.just(tempFilePath.toFile()));
            return fileMono.block();
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new VaultException(e.getMessage());
        }
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
        val bytesSent = new AtomicLong(0);
        val lastLoggedPercentage = new AtomicInteger(0);
        // workaround for nodesApi.updateNodeContent()
        webClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/nodes/{nodeId}/content")
                        .build(nodeId))
                .header(HttpHeaders.AUTHORIZATION, basicAuth)
                .contentType(MediaType.valueOf(nodeContent.getContentType()))
                .body(BodyInserters.fromDataBuffers(
                        DataBufferUtils.readInputStream(nodeContent::getContentStream, DefaultDataBufferFactory.sharedInstance, 8192)
                                .doOnNext(buffer -> {
                                    val sent = bytesSent.addAndGet(buffer.readableByteCount());
                                    if (nodeContent.getLength() > 0) {
                                        val percentage = (int) ((double) sent / nodeContent.getLength() * 100);
                                        if (sent == nodeContent.getLength() || percentage >= lastLoggedPercentage.get() + 10) {
                                            lastLoggedPercentage.set((percentage / 10) * 10);
                                            log.trace("Upload progress for node {}: {} bytes sent ({}% out of {} bytes)",
                                                    nodeId, sent, percentage, nodeContent.getLength());
                                        }
                                    }
                                })
                ))
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(log::debug)
                .doOnError(error -> log.error(error.getMessage()))
                .subscribe();
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