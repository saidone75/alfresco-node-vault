/*
 * Alfresco Node Vault - ad aeternam documentorum conservationem
 * Copyright (C) 2025-2026 Saidone
 * SPDX-License-Identifier: GPL-3.0-or-later
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

package org.saidone.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.model.entity.NodeEntity;
import org.saidone.service.locking.VaultOperationCoordinator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/** Synchronizes optional property indexes after startup under a renewable distributed job lock. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PropertyIndexSynchronizer {

    private static final String PROPERTY_INDEX_PREFIX = "anv_property_";

    private final MongoOperations mongoOperations;
    private final VaultOperationCoordinator coordinator;

    @Value("${application.advanced-search.indexed-properties:}")
    private List<String> indexedProperties;

    /** Existing queries remain available while missing indexes are built. */
    @Async("taskExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeAfterStartup() {
        try {
            coordinator.runJob("property-index-sync", this::syncPropertyIndexes);
        } catch (Exception e) {
            log.warn("Unable to update the indexes on node properties: {}", e.getMessage());
        }
    }

    private void syncPropertyIndexes() {
        log.info("Starting node property index synchronization");
        val indexOps = mongoOperations.indexOps(NodeEntity.class);
        val properties = indexedProperties.stream().map(String::trim).filter(p -> !p.isEmpty()).distinct().toList();
        val names = properties.stream().map(PropertyIndexSynchronizer::propertyIndexName).toList();
        for (val index : indexOps.getIndexInfo()) {
            if (index.getName().startsWith(PROPERTY_INDEX_PREFIX) && !names.contains(index.getName())) {
                indexOps.dropIndex(index.getName());
                log.info("Dropped index {}: property no longer configured", index.getName());
            }
        }
        for (val property : properties) {
            indexOps.createIndex(new Index()
                    .on(String.format("nb.properties.%s", property), Sort.Direction.ASC)
                    .sparse()
                    .named(propertyIndexName(property)));
        }
        log.info("Completed node property index synchronization");
    }

    private static String propertyIndexName(String property) {
        return String.format("%s%s", PROPERTY_INDEX_PREFIX, property);
    }
}
