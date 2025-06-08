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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.HashMap;

@Component
@ConditionalOnProperty(name = "application.vault.audit.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AuditWebFilter implements WebFilter {

    private final AuditService auditService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        val request = exchange.getRequest();
        val metadata = new HashMap<String, Object>();
        if (request.getRemoteAddress() != null) {
            metadata.put("ip", request.getRemoteAddress().getAddress().getHostAddress());
        }
        metadata.put("userAgent", request.getHeaders().getFirst("User-Agent"));
        metadata.put("path", request.getPath().value());
        metadata.put("method", request.getMethod());
        auditService.saveEntry(metadata, "request");
        return chain.filter(exchange).doFinally(signal -> {
            val responseData = new HashMap<String, Object>();
            responseData.put("status", exchange.getResponse().getStatusCode() != null ?
                    exchange.getResponse().getStatusCode().value() : null);
            responseData.put("path", request.getPath().value());
            auditService.saveEntry(responseData, "response");
        });
    }

}

