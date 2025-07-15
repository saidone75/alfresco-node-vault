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

package org.saidone.audit;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import static org.saidone.audit.AuditMetadataKeys.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * Entity representing a single audit entry stored in MongoDB.
 *
 * <p>An entry records the timestamp of the event, a map of associated metadata
 * (typically request or response details) and the type of the event.</p>
 */
@Data
@Document(collection = "vault_audit")
public class AuditEntry {

    @Id
    private String id;

    @Field(TIMESTAMP)
    private Instant timestamp;

    @Field(METADATA)
    private Map<String, Serializable> metadata;

    @Field(TYPE)
    private String type;

}
