package org.saidone.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.saidone.component.BaseComponent;
import org.saidone.model.NodeWrapper;
import org.saidone.repository.GridFsRepositoryImpl;
import org.saidone.repository.MongoNodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@ConditionalOnProperty(name = "application.proxy.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ProxyService extends BaseComponent {

    private final GridFsRepositoryImpl gridFsRepositoryImpl;
    private final MongoNodeRepository mongoNodeRepository;

    @Value("${application.proxy.port}")
    private int proxyPort;

    @Value("${application.proxy.target.host}")
    private String targetHost;

    @Value("${application.proxy.target.port}")
    private int targetPort;

    private HttpProxyServer proxyServer;

    @PostConstruct
    @Override
    public void init() {
        super.init();
        proxyServer = DefaultHttpProxyServer.bootstrap()
                .withPort(proxyPort)
                .withFiltersSource(new HttpFiltersSourceAdapter() {
                    @Override
                    public HttpFilters filterRequest(@Nonnull HttpRequest originalRequest) {
                        return new HttpFiltersAdapter(originalRequest) {
                            @Override
                            public HttpResponse clientToProxyRequest(@Nonnull HttpObject httpObject) {
                                if (httpObject instanceof HttpRequest httpRequest) {
                                    var uri = httpRequest.uri();
                                    // Intercept paths
                                    log.trace(uri);
                                    var nodePattern = Pattern.compile("^/alfresco/api/-default-/public/alfresco/versions/1/nodes/(.{36})$");
                                    var matcher = nodePattern.matcher(uri);
                                    if (matcher.matches()) {
                                        log.debug("Path intercepted => {}", uri);
                                        var nodeId = matcher.group(1);
                                        var nodeOptional = mongoNodeRepository.findById(nodeId);
                                        if (nodeOptional.isPresent()) {
                                            log.debug("Node found on MongoDB => {}", nodeId);
                                            var nodeWrapper = nodeOptional.get();
                                            return getNodeResponse(nodeWrapper);
                                        }
                                    }
                                    var nodeContentPattern = Pattern.compile("^/alfresco/api/-default-/public/alfresco/versions/1/nodes/(.{36})/content(\\?attachment=(true|false))?$");
                                    matcher = nodeContentPattern.matcher(uri);
                                    if (matcher.matches()) {
                                        log.debug("Path intercepted => {}", uri);
                                        var nodeId = matcher.group(1);
                                        var attachment = matcher.group(3);
                                        var gridFsFile = gridFsRepositoryImpl.findFileById(nodeId);
                                        if (gridFsFile != null) {
                                            log.debug("Node found on MongoDB => {}", nodeId);
                                            return getNodecontentResponse(gridFsFile, Boolean.parseBoolean(attachment));
                                        }
                                    }
                                    // Redirect other requests to target
                                    httpRequest.setUri(String.format("http://%s:%d%s", targetHost, targetPort, uri));
                                }
                                // Allow other requests to be forwarded
                                return null;
                            }
                        };
                    }
                })
                .withTransparent(true)
                .start();

        log.info("Proxy started on port => {}", proxyPort);
    }

    private static HttpResponse getNodeResponse(NodeWrapper nodeWrapper) {
        var response =
                new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1,
                        HttpResponseStatus.OK,
                        Unpooled.copiedBuffer(nodeWrapper.getNodeJson().getBytes())
                );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, nodeWrapper.getNodeJson().length());
        return response;
    }

    @SneakyThrows
    private HttpResponse getNodecontentResponse(GridFSFile gridFSFile, boolean attachment) {
        byte[] bytes = gridFsRepositoryImpl.getFileContent(gridFSFile).readAllBytes();
        var response =
                new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1,
                        HttpResponseStatus.OK,
                        Unpooled.copiedBuffer(bytes)
                );
        if (gridFSFile.getMetadata() != null) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, gridFSFile.getMetadata().getString("_contentType"));
        }
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        if (attachment)
            response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, String.format("attachment; filename=\"%s\"", gridFSFile.getFilename()));
        return response;
    }

    @PreDestroy
    @Override
    public void stop() {
        super.stop();
        if (proxyServer != null) {
            proxyServer.stop();
        }
    }

}