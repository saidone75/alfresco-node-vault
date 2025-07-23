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

package org.saidone.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.http.HttpHeaders;
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
 * <p>For each request the filter collects basic metadata (IP address, user agent,
 * path, HTTP method) and stores it via {@link AuditService}. When the response
 * is completed a second audit entry is stored containing the status code.</p>
 */
@Component
@ConditionalOnProperty(name = "application.service.vault.audit.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AuditWebFilter extends BaseComponent implements WebFilter {

    private final AuditService auditService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        val requestEntry = createRequestAuditEntry(exchange.getRequest());
        auditService.saveEntry(requestEntry);
        return chain.filter(exchange).doFinally(signal -> {
            val responseEntry = createResponseAuditEntry(requestEntry.getId(), exchange.getResponse());
            auditService.saveEntry(responseEntry);
        });
    }

    public static AuditEntry createRequestAuditEntry(ServerHttpRequest request) {
        val metadata = new HashMap<String, Serializable>();
        metadata.put(AuditMetadataKeys.ID, request.getId());
        if (request.getRemoteAddress() != null) {
            metadata.put(AuditMetadataKeys.IP, request.getRemoteAddress().getAddress().getHostAddress());
        }
        metadata.put(AuditMetadataKeys.USER_AGENT, request.getHeaders().getFirst(HttpHeaders.USER_AGENT));
        metadata.put(AuditMetadataKeys.PATH, request.getPath().value());
        metadata.put(AuditMetadataKeys.METHOD, request.getMethod().toString());
        val entry = new AuditEntry();
        entry.setMetadata(metadata);
        entry.setType(AuditMetadataKeys.REQUEST);
        return entry;
    }

    public static AuditEntry createResponseAuditEntry(String id, ServerHttpResponse response) {
        val metadata = new HashMap<String, Serializable>();
        metadata.put(AuditMetadataKeys.ID, id);
        metadata.put(AuditMetadataKeys.STATUS, response.getStatusCode() != null ?
                response.getStatusCode().value() : null);
        val entry = new AuditEntry();
        entry.setMetadata(metadata);
        entry.setType(AuditMetadataKeys.RESPONSE);
        return entry;
    }

}

