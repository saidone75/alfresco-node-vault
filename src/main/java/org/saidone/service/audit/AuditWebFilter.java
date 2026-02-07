/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
 * Copyright (C) 2025-2026 Saidone
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.saidone.service.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.http.HttpHeaders;
import org.jetbrains.annotations.NotNull;
import org.saidone.component.BaseComponent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.io.Serializable;
import java.util.HashMap;

/**
 * Web filter that audits incoming requests and outgoing responses.
 *
 * <p>For each request the filter collects basic metadata such as IP address,
 * {@code User-Agent}, request path and HTTP method and persists it using
 * {@link AuditServiceImpl}. After the response has been sent another audit
 * entry is stored containing at least the HTTP status code.</p>
 *
 * <p>The filter is active only when the configuration property
 * {@code application.service.vault.audit.enabled} is set to {@code true}.</p>
 */
@Component
@ConditionalOnProperty(name = "application.service.vault.audit.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AuditWebFilter extends BaseComponent implements WebFilter {

    /** Service used to persist {@link AuditEntry} instances. */
    private final AuditServiceImpl auditService;

    /**
     * Intercepts the request/response exchange to persist basic audit
     * information.
     *
     * <p>The request is audited before the rest of the filter chain executes.
     * After the chain completes, a second entry is stored describing the
     * response.</p>
     *
     * @param exchange the current server exchange
     * @param chain    the remaining web filter chain
     * @return completion signal for the filter chain
     */
    @NotNull
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        val requestEntry = createRequestAuditEntry(exchange.getRequest());
        auditService.saveEntry(requestEntry);
        return chain.filter(exchange).doFinally(signal -> {
            val responseEntry = createResponseAuditEntry(requestEntry.getId(), exchange.getResponse());
            auditService.saveEntry(responseEntry);
        });
    }

    /**
     * Build an audit entry representing an incoming HTTP request.
     *
     * <p>The metadata map of the returned entry contains information such as
     * the request identifier, client IP, {@code User-Agent}, path and HTTP
     * method.</p>
     *
     * @param request the server request
     * @return populated audit entry for the request
     */
    public static AuditEntry createRequestAuditEntry(ServerHttpRequest request) {
        val metadata = new HashMap<String, Serializable>();
        metadata.put(AuditEntryKeys.METADATA_ID, request.getId());
        if (request.getRemoteAddress() != null) {
            metadata.put(AuditEntryKeys.METADATA_IP, request.getRemoteAddress().getAddress().getHostAddress());
        }
        metadata.put(AuditEntryKeys.METADATA_USER_AGENT, request.getHeaders().getFirst(HttpHeaders.USER_AGENT));
        metadata.put(AuditEntryKeys.METADATA_PATH, request.getPath().value());
        metadata.put(AuditEntryKeys.METADATA_METHOD, request.getMethod().toString());
        val entry = new AuditEntry();
        entry.setMetadata(metadata);
        entry.setType(AuditEntryKeys.REQUEST);
        return entry;
    }

    /**
     * Build an audit entry representing an HTTP response.
     *
     * <p>The metadata map of the returned entry contains the identifier of the
     * original request and the response status code.</p>
     *
     * @param id       identifier of the related request
     * @param response the server response
     * @return populated audit entry for the response
     */
    public static AuditEntry createResponseAuditEntry(String id, ServerHttpResponse response) {
        val metadata = new HashMap<String, Serializable>();
        metadata.put(AuditEntryKeys.METADATA_ID, id);
        metadata.put(AuditEntryKeys.METADATA_STATUS, response.getStatusCode() != null ?
                response.getStatusCode().value() : null);
        val entry = new AuditEntry();
        entry.setMetadata(metadata);
        entry.setType(AuditEntryKeys.RESPONSE);
        return entry;
    }

}

