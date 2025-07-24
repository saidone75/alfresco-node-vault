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
import org.saidone.audit.AuditEntryKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Creates the MongoDB collection used for persisting {@code AuditEntry} records.
 * <p>
 * On application startup this component checks whether the collection
 * {@value COLLECTION_NAME} exists and, if not, creates it as a time series
 * collection with the {@code timestamp} field as its time key. The created
 * collection stores additional audit metadata in the {@code metadata} sub
 * document and uses a seconds granularity.
 * Documents automatically expire after {@link #ttlDays} days. The TTL value
 * can be overridden via the {@code AUDIT_TTL_DAYS} environment variable or the
 * {@code application.audit.ttl-days} property.
 * </p>
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

    /** Name of the MongoDB collection storing audit entries. */
    private static final String COLLECTION_NAME = "vault_audit";

    /**
     * Creates the audit collection if it does not already exist and configures
     * the collection to expire entries after {@link #ttlDays} days.
     */
    @PostConstruct
    @Override
    public void init() {
        super.init();
        if (!mongoTemplate.collectionExists(COLLECTION_NAME)) {
            val timeSeriesOptions = new TimeSeriesOptions(AuditEntryKeys.TIMESTAMP)
                    .metaField(AuditEntryKeys.METADATA)
                    .granularity(TimeSeriesGranularity.SECONDS);

            val options = new CreateCollectionOptions()
                    .timeSeriesOptions(timeSeriesOptions).expireAfter(ttlDays, TimeUnit.DAYS);

            mongoTemplate.getDb().createCollection(COLLECTION_NAME, options);
            log.info("Time series collection '{}' successfully created.", COLLECTION_NAME);
        } else {
            log.info("Collection '{}' already exists.", COLLECTION_NAME);
        }
        super.stop();
    }

}
