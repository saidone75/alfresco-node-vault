package org.saidone.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "application.audit.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AuditWebFilter implements WebFilter {

    private final AuditService auditService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        val request = exchange.getRequest();
        Map<String, Object> metadata = new HashMap<>();
        if (request.getRemoteAddress() != null) {
            metadata.put("ip", request.getRemoteAddress().getAddress().getHostAddress());
        }
        metadata.put("userAgent", request.getHeaders().getFirst("User-Agent"));
        metadata.put("path", request.getPath().value());
        metadata.put("method", request.getMethodValue());
        auditService.saveEntry(metadata, "request");
        return chain.filter(exchange).doFinally(signal -> {
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("status", exchange.getResponse().getStatusCode() != null ?
                    exchange.getResponse().getStatusCode().value() : null);
            responseData.put("path", request.getPath().value());
            auditService.saveEntry(responseData, "response");
        });
    }
}

