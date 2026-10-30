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

package org.saidone.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Hashes of node metadata and of the notarized node state.
 */
@UtilityClass
public class NodeDigestUtils {

    /** Writes JSON with sorted keys and no whitespace. */
    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    /**
     * Computes the hash of node metadata on their canonical JSON form, so that
     * the hash does not depend on key order or formatting, which change when
     * the metadata is stored in MongoDB and read back.
     *
     * @param nodeJson  the node metadata as JSON
     * @param algorithm the hash algorithm, e.g. {@code SHA-256}
     * @return the hexadecimal hash
     */
    @SneakyThrows
    public String metadataHash(String nodeJson, String algorithm) {
        val canonical = CANONICAL_MAPPER.writeValueAsBytes(CANONICAL_MAPPER.readValue(nodeJson, Object.class));
        return hash(canonical, algorithm);
    }

    /**
     * Computes the hash notarized for a node, binding its id to the hashes of
     * its content and metadata.
     *
     * @param nodeId       the node identifier
     * @param contentHash  the hash of the node content
     * @param metadataHash the hash of the node metadata
     * @param algorithm    the hash algorithm used for all three hashes
     * @return the hexadecimal hash
     */
    public String notarizationHash(String nodeId, String contentHash, String metadataHash, String algorithm) {
        val data = String.format("anv-notarization:%s:%s:%s", nodeId, contentHash, metadataHash);
        return hash(data.getBytes(StandardCharsets.UTF_8), algorithm);
    }

    @SneakyThrows
    private String hash(byte[] data, String algorithm) {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data));
    }

}
