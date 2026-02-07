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

package org.saidone.service.audit;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import static org.saidone.service.audit.AuditEntryKeys.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * Entity representing a single audit entry stored in MongoDB.
 *
 * <p>An entry records the timestamp of an audited event along with a small
 * metadata map describing the request or response. The entry {@linkplain #type
 * type} and metadata keys are kept short using the constants defined in
 * {@link AuditEntryKeys} so that the persisted documents remain compact.</p>
 */
@Data
@Document(collection = AuditService.AUDIT_COLLECTION_NAME)
public class AuditEntry {

    /**
     * Unique identifier of the audit entry.
     */
    @Id
    private String id;

    /**
     * Type of the audited event. Typical values are
     * {@link AuditEntryKeys#REQUEST} or {@link AuditEntryKeys#RESPONSE}.
     */
    @Field(TYPE)
    private String type;

    /**
     * Moment in time when the event occurred.
     */
    @Field(TIMESTAMP)
    private Instant timestamp;

    /**
     * Additional metadata describing the request or response. Typical keys are
     * {@link AuditEntryKeys#METADATA_IP IP address},
     * {@link AuditEntryKeys#METADATA_USER_AGENT User-Agent} and
     * {@link AuditEntryKeys#METADATA_STATUS response status}. The exact
     * information stored depends on whether the entry represents a request or
     * a response.
     */
    @Field(METADATA)
    private Map<String, Serializable> metadata;

    /**
     * Creates a new {@code AuditEntry} with its {@linkplain #timestamp
     * timestamp} set to the current instant. Other properties can be populated
     * later before the entry is persisted via {@link AuditService}.
     */
    public AuditEntry() {
        this.timestamp = Instant.now();
    }

}
