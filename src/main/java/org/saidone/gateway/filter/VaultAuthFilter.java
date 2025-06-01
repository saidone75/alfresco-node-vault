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

package org.saidone.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.service.AlfrescoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.nio.charset.Charset;
import java.util.Base64;

@Component
@Slf4j
public class VaultAuthFilter extends AbstractGatewayFilterFactory<VaultAuthFilter.Config> {

    @Autowired
    @Lazy
    private AlfrescoService alfrescoService;

    public VaultAuthFilter() {
        super(Config.class);
    }

    public static class Config {
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            if (!isAuthorized(exchange)) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        };
    }

    private boolean isAuthorized(ServerWebExchange exchange) {
        val authorization = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authorization != null) {
            val userIdAndPassword = new String(Base64.getDecoder().decode(authorization.getFirst().split("\\s")[1]), Charset.defaultCharset()).split(":");
            return alfrescoService.isAdmin(userIdAndPassword[0], userIdAndPassword[1]);
        }
        return false;
    }

}