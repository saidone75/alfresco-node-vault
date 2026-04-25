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

package org.saidone.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Persistence entity mapped to the MongoDB {@code alf_node} collection.
 *
 * <p>This class only models storage concerns and does not contain API/domain
 * serialization logic.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "alf_node")
public class NodeEntity {

    /** Identifier of the wrapped Alfresco node. */
    @Id
    private String id;

    /** Timestamp when the node was archived. */
    @Field("adt")
    @Indexed
    private Instant archiveDate;

    /** Flag indicating whether the node has been restored. */
    @Field("res")
    private boolean restored;

    /** Flag signalling that {@link #nodeJson} is encrypted. */
    @Field("enc")
    private boolean encrypted;

    /** Version of the key used to encrypt {@link #nodeJson}. */
    @Field("kv")
    private int keyVersion;

    /** JSON representation of the node. May be encrypted. */
    @Field("nj")
    private String nodeJson;

    /** Transaction id returned from notarization, if any. */
    @Field("ntx")
    private String notarizationTxId;

}
