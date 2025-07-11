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

package org.saidone.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Metadata describing the archived content of a node. Extends
 * {@link NodeContent} with additional information such as checksum and
 * encryption status.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class NodeContentInfo extends NodeContent {

    /** Identifier of the stored content (e.g. GridFS id). */
    @Field("cid")
    private String contentId;
    /** Hash algorithm used to generate {@link #contentHash}. */
    @Field("alg")
    private String contentHashAlgorithm;
    /** Hexadecimal checksum of the content. */
    @Field("hash")
    private String contentHash;
    /** Flag indicating whether the binary data is encrypted. */
    @Field("enc")
    private boolean encrypted;

}
