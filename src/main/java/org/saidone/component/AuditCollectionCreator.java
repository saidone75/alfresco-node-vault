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
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AuditCollectionCreator extends BaseComponent {

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final String COLLECTION_NAME = "vault_audit";

    @PostConstruct
    @Override
    public void init() {
        super.init();
        if (!mongoTemplate.collectionExists(COLLECTION_NAME)) {
            val timeSeriesOptions = new TimeSeriesOptions("timestamp")
                    .metaField("metadata")
                    .granularity(TimeSeriesGranularity.SECONDS);

            val options = new CreateCollectionOptions()
                    .timeSeriesOptions(timeSeriesOptions);

            mongoTemplate.getDb().createCollection(COLLECTION_NAME, options);
           log.info("Time series collection '{}' successfully created.", COLLECTION_NAME);
        } else {
            log.info("Collection '{}' already exists.", COLLECTION_NAME);
        }
    }

}
