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

package org.saidone.gateway.predicate;

import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.saidone.repository.MongoNodeRepository;
import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.function.Predicate;
import java.util.regex.Pattern;

@Component
@Slf4j
public class IsOnVaultPredicate extends AbstractRoutePredicateFactory<IsOnVaultPredicate.Config> {

    private final MongoNodeRepository mongoNodeRepository;

    public IsOnVaultPredicate(MongoNodeRepository mongoNodeRepository) {
        super(IsOnVaultPredicate.Config.class);
        this.mongoNodeRepository = mongoNodeRepository;
    }

    @Override
    public Predicate<ServerWebExchange> apply(Config config) {
        return exchange -> {
            var path = exchange.getRequest().getURI().getPath();
            var nodeContentPattern = Pattern.compile("^.*/nodes/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}).*$");
            var matcher = nodeContentPattern.matcher(path);
            if (matcher.matches()) {
                var nodeId = matcher.group(1);
                log.debug("Detected node ID => {}", nodeId);
                var isOnVault = mongoNodeRepository.findById(nodeId).isPresent();
                log.debug("Node {} {}found on the vault", nodeId, isOnVault ? Strings.EMPTY : "NOT ");
                return isOnVault;
            }
            return false;
        };
    }

    public static class Config {
    }

}