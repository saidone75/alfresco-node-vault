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
import org.saidone.component.BaseComponent;
import org.saidone.misc.AnvDigestInputStream;
import org.saidone.model.MetadataKeys;
import org.saidone.model.dto.NodeContentInfoDto;
import org.saidone.model.dto.NodeContentStreamDto;
import org.saidone.repository.OzoneRepository;
import org.saidone.utils.SecureTemporaryFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;

/**
 * {@link ContentService} implementation that stores node binaries in Apache Ozone.
 * <p>
 * During archival the content stream is written to a temporary file, encrypted when
 * content encryption is enabled, so the checksum of the original content and the
 * stored length are known before uploading. The resulting hash and other metadata are persisted as
 * Ozone key metadata. Retrieval operations return {@link NodeContentStreamDto} and
 * {@link NodeContentInfoDto} descriptors built from stored key details and content streams.
 * </p>
 * <p>
 * This service is enabled when {@code application.service.vault.storage.impl} is set to
 * {@code ozone}.
 * </p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.service.vault.storage.impl", havingValue = "ozone")
public class OzoneContentService extends BaseComponent implements ContentService {

    private final OzoneRepository ozoneRepository;

    @Value("${application.service.vault.hash-algorithm}")
    private String checksumAlgorithm;

    /**
     * {@inheritDoc}
     */
    @Override
    @SneakyThrows
    public void archiveNodeContent(Node node, InputStream inputStream) {
        // The source stream is a resource too, so it is closed even if the temporary file cannot be created.
        try (inputStream;
             val temporary = new SecureTemporaryFile("ozone-upload-", ".bin")) {
            val metadata = new HashMap<String, String>();
            String checksum;
            long plaintextLength;
            // Encrypting while writing keeps plain content off the disk and needs a single temporary file.
            try (val counter = BoundedInputStream.builder().setInputStream(inputStream).get();
                 val digest = new AnvDigestInputStream(counter, checksumAlgorithm);
                 val stored = ozoneRepository.prepareContent(digest, metadata);
                 val output = Files.newOutputStream(temporary.getPath())) {
                stored.transferTo(output);
                checksum = digest.getHash();
                plaintextLength = counter.getCount();
            }
            metadata.put(MetadataKeys.UUID, node.getId());
            metadata.put(MetadataKeys.FILENAME, node.getName());
            metadata.put(MetadataKeys.CONTENT_TYPE, node.getContent().getMimeType());
            metadata.put(MetadataKeys.CHECKSUM_ALGORITHM, checksumAlgorithm);
            metadata.put(MetadataKeys.CHECKSUM_VALUE, checksum);
            metadata.put(OzoneRepository.PLAINTEXT_LENGTH, Long.toString(plaintextLength));
            ozoneRepository.putObject(node.getId(), temporary.getPath(), metadata);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SneakyThrows
    public NodeContentStreamDto getNodeContent(String nodeId) {
        val details = ozoneRepository.getObjectDetails(nodeId);
        val result = new NodeContentStreamDto();
        result.setFileName(details.getMetadata().getOrDefault(MetadataKeys.FILENAME, nodeId));
        result.setContentType(details.getMetadata().get(MetadataKeys.CONTENT_TYPE));
        result.setLength(Long.parseLong(details.getMetadata().getOrDefault(
                OzoneRepository.PLAINTEXT_LENGTH, Long.toString(details.getDataSize()))));
        result.setContentStream(ozoneRepository.getObject(nodeId, details));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SneakyThrows
    public NodeContentInfoDto getNodeContentInfo(String nodeId) {
        val metadata = ozoneRepository.getObjectDetails(nodeId).getMetadata();
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
        ozoneRepository.deleteObject(nodeId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SneakyThrows
    public String computeHash(String nodeId, String algorithm) {
        val details = ozoneRepository.getObjectDetails(nodeId);
        try (val digest = new AnvDigestInputStream(ozoneRepository.getObject(nodeId, details), algorithm)) {
            digest.transferTo(OutputStream.nullOutputStream());
            return digest.getHash();
        }
    }
}
