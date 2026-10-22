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
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Default {@link OzoneRepository} implementation relying on the native Ozone RPC
 * client {@link OzoneBucket}. The class exposes basic methods to upload and download
 * objects using a provided {@code OzoneBucket} instance.
 * <p>
 * The bean is created only when {@code application.service.vault.storage.impl}
 * equals {@code "ozone"}.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${application.service.vault.storage.impl}' == 'ozone'")
public class OzoneRepositoryImpl implements OzoneRepository {

    /**
     * Ozone bucket used to perform the requests. Its replication policy is used
     * when uploading objects.
     */
    private final OzoneBucket bucket;

    /**
     * {@inheritDoc}
     */
    @Override
    public void putObject(String nodeId, Path content, Map<String, String> metadata) throws IOException {
        try (val input = Files.newInputStream(content);
             val output = bucket.createKey(nodeId, Files.size(content), bucket.getReplicationConfig(), metadata)) {
            input.transferTo(output);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream getObject(String nodeId) throws IOException {
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
