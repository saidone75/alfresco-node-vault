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

package org.saidone.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.Document;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
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
@org.springframework.data.mongodb.core.mapping.Document(collection = "alf_node")
@CompoundIndexes({
        // Notarization and key rotation page node IDs by these fields.
        @CompoundIndex(name = "ntx_id", def = "{'ntx': 1, '_id': 1}"),
        @CompoundIndex(name = "kv_id", def = "{'kv': 1, '_id': 1}"),
        // Advanced search; sparse, so nodes with encrypted metadata take no space.
        @CompoundIndex(name = "node_type", def = "{'nb.nodeType': 1}", sparse = true),
        @CompoundIndex(name = "mime_type", def = "{'nb.content.mimeType': 1}", sparse = true),
        @CompoundIndex(name = "content_size", def = "{'nb.content.sizeInBytes': 1}", sparse = true),
        @CompoundIndex(name = "aspect_names", def = "{'nb.aspectNames': 1}", sparse = true)
})
public class NodeEntity {

    /** Identifier of the wrapped Alfresco node. */
    @Id
    private String id;

    /** Timestamp when the node was archived. */
    @Field("adt")
    @Indexed
    private Instant archiveDate;

    /** Identifier of the node created on Alfresco by the last restore, if any. */
    @Field("rnid")
    private String restoredNodeId;

    /** Flag signalling that the node is stored encrypted in {@link #nodeJson}. */
    @Field("enc")
    private boolean encrypted;

    /** Version of the key used to encrypt {@link #nodeJson}. */
    @Field("kv")
    private int keyVersion;

    /** BSON representation of the node. Set only when the node is not encrypted. */
    @Field("nb")
    private Document nodeDocument;

    /** Encrypted JSON representation of the node. Set only when {@link #encrypted} is {@code true}. */
    @Field("nj")
    private String nodeJson;

    /** Hash of the node metadata recorded at archive time. */
    @Field("mh")
    private String metadataHash;

    /** Algorithm of {@link #metadataHash}, also used for the notarization hash. */
    @Field("mha")
    private String metadataHashAlgorithm;

    /** Transaction id returned from notarization, if any. */
    @Field("ntx")
    private String notarizationTxId;

}
