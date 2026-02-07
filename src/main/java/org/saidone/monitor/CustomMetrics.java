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

package org.saidone.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Registers and exposes application specific metrics using Micrometer.
 * <p>
 * Currently exposes a gauge metric named {@code nodes_on_vault} reporting the
 * number of documents stored in the {@code alf_node} MongoDB collection.
 */
@Component
public class CustomMetrics {

    private final MeterRegistry meterRegistry;
    private final MongoTemplate mongoTemplate;

    /**
     * Creates a new instance and registers the custom gauge metric.
     *
     * @param meterRegistry the registry to register metrics with
     * @param mongoTemplate template used to query MongoDB collections
     */
    public CustomMetrics(MeterRegistry meterRegistry, MongoTemplate mongoTemplate) {
        this.meterRegistry = meterRegistry;
        this.mongoTemplate = mongoTemplate;
        Gauge.builder("nodes_on_vault", this, CustomMetrics::getAlfNodeCount)
                .description("Nodes on vault")
                .tags(Tags.of("vault", "nodes"))
                .register(meterRegistry);
    }

    /**
     * Retrieves the count of nodes currently stored on the vault.
     *
     * @return number of documents in the {@code alf_node} collection
     */
    private long getAlfNodeCount() {
        return mongoTemplate.getCollection("alf_node").countDocuments();
    }

}