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

package org.saidone.service;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.core.model.Node;
import org.saidone.exception.NodeNotFoundOnVaultException;
import org.saidone.misc.AnvDigestInputStream;
import org.saidone.model.MetadataKeys;
import org.saidone.model.NodeContent;
import org.saidone.repository.GridFsRepositoryImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;

@RequiredArgsConstructor
@Service
@Slf4j
public class GridFsContentService implements ContentService {

    @Value("${application.service.vault.hash-algorithm}")
    private String checksumAlgorithm;

    private final GridFsRepositoryImpl gridFsRepository;

    @Override
    /**
     * Archives the content of the given node by saving the content stream into a GridFS repository
     * with associated metadata. The input stream is wrapped in a DigestInputStream to compute a checksum
     * using the configured checksum algorithm during the save operation. After saving, the method also
     * updates the file metadata with the checksum algorithm and the computed checksum value.
     *
     * @param node        the node whose content is to be archived
     * @param inputStream the input stream of the node's content to be saved and checksummed
     */
    @SneakyThrows
    public void archiveNodeContent(Node node, InputStream inputStream) {
        try (val digestInputStream = new AnvDigestInputStream(inputStream, checksumAlgorithm)) {
            gridFsRepository.saveFile(
                    digestInputStream,
                    node.getName(),
                    node.getContent().getMimeType(),
                    new HashMap<>() {{
                        put(MetadataKeys.UUID, node.getId());
                    }});
            val hash = digestInputStream.getHash();
            log.trace("{}: {}", checksumAlgorithm, hash);
            gridFsRepository.updateFileMetadata(
                    node.getId(),
                    new HashMap<>() {{
                        put(MetadataKeys.CHECKSUM_ALGORITHM, checksumAlgorithm);
                        put(MetadataKeys.CHECKSUM_VALUE, hash);
                    }});
        }
    }

    @Override
    /**
     * Retrieves the content of a node stored in GridFS by node ID.
     *
     * @param nodeId the ID of the node
     * @return the NodeContent containing file name, content type, length, and content stream
     * @throws NodeNotFoundOnVaultException if the node content is not found in the vault
     */
    public NodeContent getNodeContent(String nodeId) {
        val gridFSFile = gridFsRepository.findFileById(nodeId);
        if (gridFSFile == null) {
            throw new NodeNotFoundOnVaultException(nodeId);
        }
        val nodeContent = new NodeContent();
        nodeContent.setFileName(gridFSFile.getFilename());
        if (gridFSFile.getMetadata() != null && gridFSFile.getMetadata().containsKey(MetadataKeys.CONTENT_TYPE)) {
            nodeContent.setContentType(gridFSFile.getMetadata().getString(MetadataKeys.CONTENT_TYPE));
        }
        nodeContent.setLength(gridFSFile.getLength());
        nodeContent.setContentStream(gridFsRepository.getFileContent(gridFSFile));
        return nodeContent;
    }

    @Override
    public void deleteFileById(String nodeId) {
        gridFsRepository.deleteFileById(nodeId);
    }

    @Override
    public String computeHash(String nodeId, String algorithm) {
        return gridFsRepository.computeHash(nodeId, algorithm);
    }

}
