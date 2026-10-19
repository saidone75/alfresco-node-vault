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

package org.saidone.service.content;

import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.SneakyThrows;
import org.alfresco.core.model.Node;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.hadoop.ozone.client.OzoneBucket;
import org.apache.hadoop.ozone.client.OzoneKeyDetails;
import org.apache.hadoop.ozone.om.exceptions.OMException;
import org.saidone.component.BaseComponent;
import org.saidone.exception.NodeNotFoundOnVaultException;
import org.saidone.misc.AnvDigestInputStream;
import org.saidone.model.MetadataKeys;
import org.saidone.model.dto.NodeContentInfoDto;
import org.saidone.model.dto.NodeContentStreamDto;
import org.saidone.service.SecretService;
import org.saidone.service.crypto.CryptoService;
import org.saidone.utils.SecureTemporaryFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;

/**
 * {@link ContentService} implementation backed by the native Ozone RPC client.
 * <p>
 * Stores content descriptors alongside binaries and computes plaintext checksums.
 * Uploads are staged in private temporary files to determine the stored length,
 * including optional encryption overhead, without buffering entire files in memory.
 * </p>
 * <p>
 * Enabled when {@code application.service.vault.storage.impl} is {@code ozone}.
 * </p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.service.vault.storage.impl", havingValue = "ozone")
public class OzoneContentService extends BaseComponent implements ContentService {

    private static final String PLAINTEXT_LENGTH = "plaintext-length";

    /** Existing bucket whose replication policy is used for uploads. */
    private final OzoneBucket bucket;
    /** Stream encryption and decryption shared with the other storage backends. */
    private final CryptoService cryptoService;
    /** Provides versioned secrets for encrypted uploads. */
    private final SecretService secretService;

    @Value("${application.service.vault.hash-algorithm}")
    private String checksumAlgorithm;

    @Value("${application.service.vault.encryption.content.enabled:false}")
    private boolean encryptContent;

    /**
     * {@inheritDoc}
     */
    @Override
    @SneakyThrows
    public void archiveNodeContent(Node node, InputStream inputStream) {
        // Stage on disk to know the exact stored length, including encryption overhead.
        // The checksum always describes the plaintext.
        try (val temporary = new SecureTemporaryFile("ozone-upload-", ".bin");
             val counted = BoundedInputStream.builder().setInputStream(inputStream).setMaxCount(-1).get();
             val digest = new AnvDigestInputStream(counted, checksumAlgorithm)) {
            val metadata = new HashMap<String, String>();
            metadata.put(MetadataKeys.UUID, node.getId());
            metadata.put(MetadataKeys.FILENAME, node.getName());
            metadata.put(MetadataKeys.CONTENT_TYPE, node.getContent().getMimeType());
            metadata.put(MetadataKeys.CHECKSUM_ALGORITHM, checksumAlgorithm);
            metadata.put(MetadataKeys.ENCRYPTED, Boolean.toString(encryptContent));
            InputStream payload = digest;
            if (encryptContent) {
                val secret = secretService.getSecret();
                metadata.put(MetadataKeys.KEY_VERSION, Integer.toString(secret.getVersion()));
                payload = cryptoService.encrypt(digest, secret);
            }
            try (val stored = payload; val output = Files.newOutputStream(temporary.getPath())) {
                stored.transferTo(output);
            }
            metadata.put(MetadataKeys.CHECKSUM_VALUE, digest.getHash());
            metadata.put(PLAINTEXT_LENGTH, Long.toString(counted.getCount()));
            try (val source = Files.newInputStream(temporary.getPath());
                 val output = bucket.createKey(node.getId(), Files.size(temporary.getPath()),
                         bucket.getReplicationConfig(), metadata)) {
                source.transferTo(output);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SneakyThrows
    public NodeContentStreamDto getNodeContent(String nodeId) {
        val details = details(nodeId);
        val result = new NodeContentStreamDto();
        result.setFileName(details.getMetadata().getOrDefault(MetadataKeys.FILENAME, nodeId));
        result.setContentType(details.getMetadata().get(MetadataKeys.CONTENT_TYPE));
        result.setLength(Long.parseLong(details.getMetadata().getOrDefault(
                PLAINTEXT_LENGTH, Long.toString(details.getDataSize()))));
        result.setContentStream(read(nodeId, details));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SneakyThrows
    public NodeContentInfoDto getNodeContentInfo(String nodeId) {
        val metadata = details(nodeId).getMetadata();
        val result = new NodeContentInfoDto();
        result.setContentId(nodeId);
        result.setFileName(metadata.getOrDefault(MetadataKeys.FILENAME, nodeId));
        result.setContentType(metadata.get(MetadataKeys.CONTENT_TYPE));
        result.setContentHashAlgorithm(metadata.get(MetadataKeys.CHECKSUM_ALGORITHM));
        result.setContentHash(metadata.get(MetadataKeys.CHECKSUM_VALUE));
        result.setEncrypted(Boolean.parseBoolean(metadata.get(MetadataKeys.ENCRYPTED)));
        result.setKeyVersion(Integer.parseInt(metadata.getOrDefault(MetadataKeys.KEY_VERSION, "0")));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SneakyThrows
    public void deleteNodeContent(String nodeId) {
        try {
            bucket.deleteKey(nodeId);
        } catch (OMException e) {
            if (e.getResult() != OMException.ResultCodes.KEY_NOT_FOUND) {
                throw e;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SneakyThrows
    public String computeHash(String nodeId, String algorithm) {
        try (val digest = new AnvDigestInputStream(read(nodeId, details(nodeId)), algorithm)) {
            digest.transferTo(OutputStream.nullOutputStream());
            return digest.getHash();
        }
    }

    private OzoneKeyDetails details(String nodeId) throws IOException {
        try {
            return bucket.getKey(nodeId);
        } catch (OMException e) {
            if (e.getResult() == OMException.ResultCodes.KEY_NOT_FOUND) {
                throw new NodeNotFoundOnVaultException(nodeId);
            }
            throw e;
        }
    }

    private InputStream read(String nodeId, OzoneKeyDetails details) throws IOException {
        InputStream stream;
        try {
            stream = bucket.readKey(nodeId);
        } catch (OMException e) {
            if (e.getResult() == OMException.ResultCodes.KEY_NOT_FOUND) {
                throw new NodeNotFoundOnVaultException(nodeId);
            }
            throw e;
        }
        try {
            return Boolean.parseBoolean(details.getMetadata().get(MetadataKeys.ENCRYPTED))
                    ? cryptoService.decrypt(stream) : stream;
        } catch (RuntimeException e) {
            try {
                stream.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }
}
