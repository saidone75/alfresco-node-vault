/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
 * Copyright (C) 2025 Saidone
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

package org.saidone.component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Ensures the primary Node collection exists and provides an index on the
 * archive date field for efficient queries.
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class NodeCollectionCreator extends BaseComponent {

    /** Name of the MongoDB collection storing archived nodes. */
    private static final String COLLECTION_NAME = "alf_node";

    /** Template used to execute MongoDB operations. */
    private final MongoTemplate mongoTemplate;

    /**
     * Creates the {@code alf_node} collection if missing and adds an index on
     * the {@code adt} field to speed up archive date lookups.
     */
    @PostConstruct
    @Override
    public void init() {
        super.init();
        try {
            if (!mongoTemplate.collectionExists(COLLECTION_NAME)) {
                mongoTemplate.createCollection(COLLECTION_NAME);
                log.info("Collection '{}' successfully created.", COLLECTION_NAME);
            } else {
                log.info("Collection '{}' already exists.", COLLECTION_NAME);
            }
            val index = new Index().on("adt", Sort.Direction.ASC).named("adt_index");
            mongoTemplate.indexOps(COLLECTION_NAME).ensureIndex(index);
        } catch (Exception e) {
            log.error("Unable to start {}", this.getClass().getSimpleName());
            super.shutDown(0);
        }
        super.stop();
    }
}
