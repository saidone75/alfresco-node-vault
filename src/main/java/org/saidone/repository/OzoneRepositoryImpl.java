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

import lombok.val;

import lombok.RequiredArgsConstructor;
import org.apache.hadoop.ozone.client.OzoneBucket;
import org.apache.hadoop.ozone.client.OzoneKeyDetails;
import org.apache.hadoop.ozone.om.exceptions.OMException;
import org.saidone.exception.NodeNotFoundOnVaultException;
import org.saidone.model.MetadataKeys;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Default {@link OzoneRepository} implementation relying on the native Ozone RPC
 * client {@link OzoneBucket}. The class exposes basic methods to upload and download
 * objects using a provided {@code OzoneBucket} instance.
 * <p>
 * The bean is created only when {@code application.service.vault.encryption.content.enabled}
 * is set to {@code false} or omitted and {@code application.service.vault.storage.impl}
 * equals {@code "ozone"}.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${application.service.vault.storage.impl}' == 'ozone' and '${application.service.vault.encryption.content.enabled:false}' == 'false'")
public class OzoneRepositoryImpl implements OzoneRepository {

    /**
     * Ozone bucket used to perform the requests. Its replication policy is used
     * when uploading objects.
     */
    protected final OzoneBucket bucket;

    /**
     * {@inheritDoc}
     */
    @Override
    public void putObject(String nodeId, long length, Map<String, String> metadata, InputStream content)
            throws IOException {
        metadata.put(MetadataKeys.ENCRYPTED, Boolean.FALSE.toString());
        metadata.put(PLAINTEXT_LENGTH, Long.toString(length));
        writeObject(nodeId, length, metadata, content);
    }

    /**
     * Uploads the provided stream as an object to Ozone, preserving the supplied
     * metadata. Shared by the plaintext and encrypted implementations.
     *
     * @param nodeId   identifier of the node / object key
     * @param length   exact stored content length, including any encryption overhead
     * @param metadata object metadata to associate with the stored content
     * @param content  stream of the content to store
     * @throws IOException if the content cannot be stored
     */
    protected void writeObject(String nodeId, long length, Map<String, String> metadata, InputStream content) throws IOException {
        try (val output = bucket.createKey(nodeId, length, bucket.getReplicationConfig(), metadata)) {
            content.transferTo(output);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream getObject(String nodeId, OzoneKeyDetails details) throws IOException {
        try {
            return bucket.readKey(nodeId);
        } catch (OMException e) {
            if (e.getResult() == OMException.ResultCodes.KEY_NOT_FOUND) {
                throw new NodeNotFoundOnVaultException(nodeId);
            }
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OzoneKeyDetails getObjectDetails(String nodeId) throws IOException {
        try {
            return bucket.getKey(nodeId);
        } catch (OMException e) {
            if (e.getResult() == OMException.ResultCodes.KEY_NOT_FOUND) {
                throw new NodeNotFoundOnVaultException(nodeId);
            }
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteObject(String nodeId) throws IOException {
        try {
            bucket.deleteKey(nodeId);
        } catch (OMException e) {
            if (e.getResult() != OMException.ResultCodes.KEY_NOT_FOUND) {
                throw e;
            }
        }
    }

}
