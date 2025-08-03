/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
 * Copyright (C) 2025 Saidone
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

import com.mongodb.client.gridfs.model.GridFSFile;

import java.io.InputStream;
import java.util.Map;

/**
 * Abstraction for storing binary content in MongoDB's GridFS bucket. Provides
 * basic CRUD operations along with helper methods for metadata management and
 * hash calculation.
 */
public interface GridFsRepository {

    /**
     * Stores the provided stream as a file.
     *
     * @param inputStream the file content
     * @param fileName    the file name to persist
     * @param contentType MIME type associated with the file
     * @param metadata    metadata key/value pairs
     */
    void saveFile(InputStream inputStream, String fileName, String contentType, Map<String, String> metadata);

    /**
     * Updates metadata for an existing stored file.
     *
     * @param uuid     identifier of the file
     * @param metadata metadata to merge with the existing entry
     */
    void updateFileMetadata(String uuid, Map<String, String> metadata);

    /**
     * Retrieves a file descriptor by UUID.
     *
     * @param uuid identifier of the file
     * @return the matching {@link GridFSFile} or {@code null}
     */
    GridFSFile findFileById(String uuid);

    /**
     * Removes a file and its metadata from GridFS.
     *
     * @param uuid identifier of the file
     */
    void deleteFileById(String uuid);

    /**
     * Calculates the cryptographic hash of a stored file.
     *
     * @param uuid      identifier of the file
     * @param algorithm name of the hash algorithm (e.g. MD5 or SHA-256)
     * @return the hexadecimal encoded hash value
     */
    String computeHash(String uuid, String algorithm);

}