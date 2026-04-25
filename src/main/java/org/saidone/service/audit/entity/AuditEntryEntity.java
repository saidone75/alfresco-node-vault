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

package org.saidone.service.audit.entity;

import lombok.Data;
import org.saidone.service.audit.AuditService;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.TimeSeries;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

import static org.saidone.service.audit.AuditEntryKeys.*;

/**
 * Persistence entity for MongoDB audit entries.
 */
@Data
@Document(collection = AuditService.AUDIT_COLLECTION_NAME)
@TimeSeries(
        collection = AuditService.AUDIT_COLLECTION_NAME,
        timeField = TIMESTAMP,
        metaField = METADATA,
        expireAfter = "60d"
)
public class AuditEntryEntity {

    @Field(TYPE)
    private String type;

    @Field(TIMESTAMP)
    private Instant timestamp;

    @Field(METADATA)
    private Map<String, Serializable> metadata;

}
