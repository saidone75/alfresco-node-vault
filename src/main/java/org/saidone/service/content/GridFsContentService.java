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

import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.core.model.Node;
import org.saidone.component.BaseComponent;
import org.saidone.exception.NodeNotFoundOnVaultException;
import org.saidone.misc.AnvDigestInputStream;
import org.saidone.model.MetadataKeys;
import org.saidone.model.dto.NodeContentInfoDto;
import org.saidone.model.dto.NodeContentStreamDto;
import org.saidone.repository.GridFsRepositoryImpl;
import org.saidone.service.crypto.ContentEncryptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link ContentService} implementation backed by MongoDB GridFS.
 * <p>
 * This class handles persisting node content streams as GridFS files and
 * retrieving them on demand. Checksums are calculated during the archive
 * operation and stored as metadata.
 * </p>
 */
@RequiredArgsConstructor
@Service
@Slf4j
@ConfigurationProperties(prefix = "application.service.vault.storage")
@ConditionalOnProperty(name = "application.service.vault.storage.impl", havingValue = "gridfs")
public class GridFsContentService extends BaseComponent implements ContentService {

    @Value("${application.service.vault.hash-algorithm}")
    private String checksumAlgorithm;

    private final GridFsRepositoryImpl gridFsRepository;
    private final ContentEncryptionService contentEncryptionService;

    /**
     * Archives the content of the given node by saving the content stream into a GridFS repository
     * with associated metadata, encrypted when content encryption is enabled. The input stream is
     * wrapped in a {@link AnvDigestInputStream} to compute a checksum of the original content using
     * the configured algorithm during the save operation. After saving, the method updates the file
     * metadata with both the algorithm name and the computed value.
     *
     * @param node        the node whose content is to be archived
     * @param inputStream the input stream of the node's content to be saved and checksummed
     */
    @Override
    @SneakyThrows
    public void archiveNodeContent(Node node, InputStream inputStream) {
        try (inputStream;
             val digestInputStream = new AnvDigestInputStream(inputStream, checksumAlgorithm)) {
            val metadata = new HashMap<String, String>();
            metadata.put(MetadataKeys.UUID, node.getId());
            metadata.put(MetadataKeys.CONTENT_TYPE, node.getContent().getMimeType());
            try (val stored = contentEncryptionService.toStoredContent(digestInputStream, metadata, node.getContent().getSizeInBytes())) {
                gridFsRepository.saveFile(
                        stored,
                        node.getName(),
                        node.getContent().getMimeType(),
                        metadata);
            }
            val hash = digestInputStream.getHash();
            log.trace("{}: {}", checksumAlgorithm, hash);
            metadata.put(MetadataKeys.CHECKSUM_ALGORITHM, checksumAlgorithm);
            metadata.put(MetadataKeys.CHECKSUM_VALUE, hash);
            gridFsRepository.updateFileMetadata(
                    node.getId(),
                    metadata);
        }
    }

    /**
     * Retrieves the content of a node stored in GridFS by node ID.
     *
     * @param nodeId the ID of the node
     * @return the {@link NodeContentStreamDto} containing file name, content type, length and the content stream
     * @throws NodeNotFoundOnVaultException if the node content is not found in the vault
     */
    @Override
    public NodeContentStreamDto getNodeContent(String nodeId) {
        val gridFSFile = findFile(nodeId);
        val nodeContent = new NodeContentStreamDto();
        nodeContent.setFileName(gridFSFile.getFilename());
        if (gridFSFile.getMetadata() != null && gridFSFile.getMetadata().containsKey(MetadataKeys.CONTENT_TYPE)) {
            nodeContent.setContentType(gridFSFile.getMetadata().getString(MetadataKeys.CONTENT_TYPE));
        }
        nodeContent.setLength(gridFSFile.getMetadata() == null ? gridFSFile.getLength()
                : Long.parseLong(gridFSFile.getMetadata().getOrDefault(MetadataKeys.ORIGINAL_LENGTH, Long.toString(gridFSFile.getLength())).toString()));
        nodeContent.setContentStream(readContent(gridFSFile));
        return nodeContent;
    }

    /**
     * Retrieves metadata information about a node's content stored in GridFS.
     *
     * @param nodeId identifier of the node
     * @return a {@link NodeContentInfoDto} describing the stored content
     * @throws NodeNotFoundOnVaultException if the node content cannot be found
     */
    @Override
    public NodeContentInfoDto getNodeContentInfo(String nodeId) {
        val gridFSFile = findFile(nodeId);
        val info = new NodeContentInfoDto();
        info.setContentId(nodeId);
        info.setFileName(gridFSFile.getFilename());
        if (gridFSFile.getMetadata() != null) {
            if (gridFSFile.getMetadata().containsKey(MetadataKeys.CONTENT_TYPE)) {
                info.setContentType(gridFSFile.getMetadata().getString(MetadataKeys.CONTENT_TYPE));
            }
            if (gridFSFile.getMetadata().containsKey(MetadataKeys.CHECKSUM_ALGORITHM)) {
                info.setContentHashAlgorithm(gridFSFile.getMetadata().getString(MetadataKeys.CHECKSUM_ALGORITHM));
            }
            if (gridFSFile.getMetadata().containsKey(MetadataKeys.CHECKSUM_VALUE)) {
                info.setContentHash(gridFSFile.getMetadata().getString(MetadataKeys.CHECKSUM_VALUE));
            }
            if (gridFSFile.getMetadata().containsKey(MetadataKeys.ENCRYPTED)) {
                info.setEncrypted(Boolean.TRUE.toString().equals(gridFSFile.getMetadata().getString(MetadataKeys.ENCRYPTED)));
            }
            if (gridFSFile.getMetadata().containsKey(MetadataKeys.KEY_VERSION)) {
                info.setKeyVersion(Integer.parseInt(gridFSFile.getMetadata().getString(MetadataKeys.KEY_VERSION)));
            }
        }
        return info;
    }

    /**
     * Removes the stored content associated with the given node identifier.
     *
     * @param nodeId the id of the node whose content should be deleted
     */
    @Override
    public void deleteNodeContent(String nodeId) {
        gridFsRepository.deleteFileById(nodeId);
    }

    /**
     * Computes the checksum of a node's content stored in GridFS using the provided algorithm.
     * Content is streamed and decrypted when needed, so the checksum always refers
     * to the original content.
     *
     * @param nodeId    identifier of the node
     * @param algorithm name of the hash algorithm
     * @return the resulting hash string
     * @throws NodeNotFoundOnVaultException if the node content cannot be found
     */
    @Override
    @SneakyThrows
    public String computeHash(String nodeId, String algorithm) {
        val gridFSFile = findFile(nodeId);
        try (val content = readContent(gridFSFile);
             val digestInputStream = new AnvDigestInputStream(content, algorithm)) {
            digestInputStream.transferTo(OutputStream.nullOutputStream());
            return digestInputStream.getHash();
        }
    }

    /**
     * Finds the stored file of a node.
     *
     * @param nodeId identifier of the node
     * @return the stored file, never {@code null}
     * @throws NodeNotFoundOnVaultException if the node content cannot be found
     */
    private GridFSFile findFile(String nodeId) {
        val gridFSFile = gridFsRepository.findFileById(nodeId);
        if (gridFSFile == null) {
            throw new NodeNotFoundOnVaultException(nodeId);
        }
        return gridFSFile;
    }

    /**
     * Reads the original content of a stored file, decrypting it when its
     * metadata marks it as encrypted.
     *
     * @param gridFSFile the stored file
     * @return the original content stream
     */
    private InputStream readContent(GridFSFile gridFSFile) {
        return contentEncryptionService.fromStoredContent(gridFsRepository.getFileContent(gridFSFile),
                gridFSFile.getMetadata() == null ? Map.of() : gridFSFile.getMetadata());
    }

}
