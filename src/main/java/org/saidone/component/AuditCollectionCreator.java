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

package org.saidone.component;

import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.TimeSeriesGranularity;
import com.mongodb.client.model.TimeSeriesOptions;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.service.audit.AuditEntryKeys;
import org.saidone.service.audit.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Creates and configures the MongoDB time series collection used to persist
 * audit entries.
 *
 * <p>The collection is initialised on startup if it does not already exist
 * and is configured with a TTL index so that documents expire automatically
 * after {@link #ttlDays} days.</p>
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class AuditCollectionCreator extends BaseComponent {

    /**
     * Number of days an audit entry is retained before expiration. The default
     * value is 60 days but it can be configured using the {@code AUDIT_TTL_DAYS}
     * environment variable or the {@code application.audit.ttl-days} property.
     */
    @Value("${AUDIT_TTL_DAYS:60}")
    private int ttlDays;

    /** Template used to execute MongoDB operations. */
    private final MongoTemplate mongoTemplate;

    /**
     * Creates the audit collection if it does not already exist and configures
     * the collection to expire entries after {@link #ttlDays} days.
     */
    @PostConstruct
    @Override
    public void init() {
        super.init();
        if (!mongoTemplate.collectionExists(AuditService.AUDIT_COLLECTION_NAME)) {
            val timeSeriesOptions = new TimeSeriesOptions(AuditEntryKeys.TIMESTAMP)
                    .metaField(AuditEntryKeys.METADATA)
                    .granularity(TimeSeriesGranularity.SECONDS);

            val options = new CreateCollectionOptions()
                    .timeSeriesOptions(timeSeriesOptions).expireAfter(ttlDays, TimeUnit.DAYS);

            mongoTemplate.getDb().createCollection(AuditService.AUDIT_COLLECTION_NAME, options);
            log.info("Time series collection '{}' successfully created.", AuditService.AUDIT_COLLECTION_NAME);
        } else {
            log.info("Collection '{}' already exists.", AuditService.AUDIT_COLLECTION_NAME);
        }
        super.stop();
    }

}
