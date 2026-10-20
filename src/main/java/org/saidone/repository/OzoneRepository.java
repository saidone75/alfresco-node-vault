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

package org.saidone.repository;

import org.apache.hadoop.ozone.client.OzoneKeyDetails;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Abstraction over the minimal set of Ozone operations required by the vault.
 * Implementations store and retrieve node content using the native Ozone RPC client.
 */
public interface OzoneRepository {

    /** Metadata field recording the original content length, before encryption. */
    String PLAINTEXT_LENGTH = "plaintext-length";

    /**
     * Uploads a node's content to the configured Ozone bucket using the node id as
     * object key.
     *
     * @param nodeId   identifier of the node / object key
     * @param length   content length before encryption, in bytes
     * @param metadata object metadata to store alongside the binary content
     * @param content  the stream providing the content to store
     * @throws IOException if the content cannot be stored
     */
    void putObject(String nodeId, long length, Map<String, String> metadata, InputStream content) throws IOException;

    /**
     * Retrieves an object from Ozone previously stored for the given node id.
     *
     * @param nodeId  identifier of the node / object key
     * @param details stored object metadata used to determine whether to decrypt
     * @return an {@link InputStream} for the object's content; the caller must close it
     * @throws IOException if the content cannot be retrieved
     */
    InputStream getObject(String nodeId, OzoneKeyDetails details) throws IOException;

    /**
     * Retrieves the object metadata for the given node id.
     * A missing object is reported as a vault node-not-found exception.
     *
     * @param nodeId identifier of the node / object key
     * @return the object metadata
     * @throws IOException if the metadata cannot be retrieved
     */
    OzoneKeyDetails getObjectDetails(String nodeId) throws IOException;

    /**
     * Deletes the object stored for the given node id. An absent object is ignored.
     *
     * @param nodeId identifier of the node / object key
     * @throws IOException if the object cannot be deleted
     */
    void deleteObject(String nodeId) throws IOException;

}
