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
import lombok.val;
import org.apache.logging.log4j.util.Strings;
import org.saidone.repository.MongoNodeRepositoryImpl;
import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Predicate implementation for Spring Cloud Gateway that checks if a node is present in the vault.
 * <p>
 * This predicate examines the path of the incoming HTTP request and tries to extract a node UUID
 * from it using a predefined regular expression. If a valid node identifier is found, it queries
 * the {@link MongoNodeRepositoryImpl} to determine whether the corresponding node is present.
 * The predicate returns {@code true} if the node exists in the vault, otherwise {@code false}.
 * <p>
 * Expected matching path format: any string containing "/nodes/{uuid}" where <i>{uuid}</i> is a
 * valid UUID version 4.
 * <p>
 * Logging is performed for debugging purposes, indicating detection of the node ID and the result
 * of the vault check.
 * <p>
 * Extends {@link AbstractRoutePredicateFactory} to be used as a custom route predicate in gateway
 * routing configurations.
 * <p>
 * The inner static {@code Config} class is required for Spring Cloud Gateway custom predicate factories.
 * <p>
 * Dependencies:
 * - {@link MongoNodeRepositoryImpl}: Used for checking node existence by ID.
 */
@Component
@Slf4j
public class IsOnVaultPredicate extends AbstractRoutePredicateFactory<IsOnVaultPredicate.Config> {

    private final MongoNodeRepositoryImpl mongoNodeRepository;

    private static final Pattern nodeContentPattern = Pattern.compile("^.*/nodes/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}).*$");

    public static class Config {
    }

    public IsOnVaultPredicate(MongoNodeRepositoryImpl mongoNodeRepository) {
        super(IsOnVaultPredicate.Config.class);
        this.mongoNodeRepository = mongoNodeRepository;
    }

    @Override
    public Predicate<ServerWebExchange> apply(Config config) {
        return exchange -> {
            val path = exchange.getRequest().getURI().getPath();
            val matcher = nodeContentPattern.matcher(path);
            if (matcher.matches()) {
                val nodeId = matcher.group(1);
                log.debug("Detected node ID: {}", nodeId);
                val isOnVault = mongoNodeRepository.findById(nodeId).isPresent();
                log.debug("Node {} {}found on the vault", nodeId, isOnVault ? Strings.EMPTY : "NOT ");
                return isOnVault;
            }
            return false;
        };
    }

}