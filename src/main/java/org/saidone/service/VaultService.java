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

import com.fasterxml.jackson.core.JsonProcessingException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.core.model.Node;
import org.saidone.component.BaseComponent;
import org.saidone.exception.HashesMismatchException;
import org.saidone.exception.NodeNotFoundOnAlfrescoException;
import org.saidone.exception.NodeNotFoundOnVaultException;
import org.saidone.exception.VaultException;
import org.saidone.misc.AnvDigestInputStream;
import org.saidone.misc.ProgressTrackingInputStream;
import org.saidone.model.MetadataKeys;
import org.saidone.model.NodeContent;
import org.saidone.model.NodeWrapper;
import org.saidone.repository.GridFsRepositoryImpl;
import org.saidone.repository.MongoNodeRepositoryImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;

/**
 * Service responsible for archiving, restoring, and managing nodes in the vault.
 * <p>
 * This service interacts with Alfresco to retrieve nodes and their content,
 * stores node metadata in MongoDB, and stores node content in GridFS.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VaultService extends BaseComponent {

    private final AlfrescoService alfrescoService;
    private final MongoNodeRepositoryImpl mongoNodeRepository;
    private final GridFsRepositoryImpl gridFsRepository;

    @Value("${application.service.vault.hash-algorithm}")
    private String checksumAlgorithm;
    @Value("${application.service.vault.double-check}")
    private boolean doubleCheck;

    private static final String DOUBLE_CHECK_ALGORITHM = "MD5";

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
    private void archiveNodeContent(Node node, InputStream inputStream) {
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

    /**
     * Archives a node by its ID.
     * <p>
     * Retrieves the node and its content from Alfresco, saves metadata to MongoDB,
     * stores the content in GridFS with checksum metadata, optionally performs a double-check
     * of the checksum, and finally deletes the node from Alfresco.
     * </p>
     *
     * @param nodeId the ID of the node to archive
     * @throws NodeNotFoundOnAlfrescoException if the node is not found in Alfresco
     * @throws VaultException if any error occurs during archiving, including rollback
     */
    public void archiveNode(String nodeId) {
        log.info("Archiving node: {}", nodeId);
        try {
            val node = alfrescoService.getNode(nodeId);
            val nodeContentInputStream = new ProgressTrackingInputStream(
                    alfrescoService.getNodeContent(nodeId),
                    nodeId,
                    node.getContent().getSizeInBytes());
            mongoNodeRepository.save(new NodeWrapper(node));
            archiveNodeContent(node, nodeContentInputStream);
            if (doubleCheck) doubleCheck(nodeId);
            alfrescoService.deleteNode(nodeId);
        } catch (FeignException.NotFound e) {
            throw new NodeNotFoundOnAlfrescoException(nodeId);
        } catch (Exception e) {
            log.trace(e.getMessage(), e);
            // rollback
            log.debug("Rollback required for node: {}", nodeId);
            mongoNodeRepository.deleteById(nodeId);
            gridFsRepository.deleteFileById(nodeId);
            throw new VaultException(String.format("Error archiving node %s: %s", nodeId, e.getMessage()));
        }
    }

    /**
     * Retrieves the wrapped node metadata from MongoDB by node ID.
     *
     * @param nodeId the ID of the node
     * @return the NodeWrapper containing node metadata
     * @throws NodeNotFoundOnVaultException if the node is not found in the vault
     */
    private NodeWrapper getNodeWrapper(String nodeId) {
        val nodeOptional = mongoNodeRepository.findById(nodeId);
        if (nodeOptional.isPresent()) {
            return nodeOptional.get();
        } else {
            throw new NodeNotFoundOnVaultException(nodeId);
        }
    }

    /**
     * Retrieves the node metadata by node ID.
     *
     * @param nodeId the ID of the node
     * @return the Alfresco Node object
     * @throws NodeNotFoundOnVaultException if the node is not found in the vault
     * @throws JsonProcessingException if there is an error processing the node metadata JSON
     */
    public Node getNode(String nodeId) throws JsonProcessingException {
        return getNodeWrapper(nodeId).getNode();
    }

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

    /**
     * Restores a node from the vault back to Alfresco.
     * <p>
     * Restores node metadata and content, optionally restoring permissions,
     * and marks the node as restored in MongoDB.
     * </p>
     *
     * @param nodeId             the ID of the node to restore
     * @param restorePermissions whether to restore permissions along with the node
     * @return the new node ID assigned by Alfresco after restoration
     * @throws NodeNotFoundOnVaultException if the node is not found in the vault
     * @throws JsonProcessingException if there is an error processing the node metadata JSON
     */
    public String restoreNode(String nodeId, boolean restorePermissions) throws JsonProcessingException {
        val nodeWrapper = getNodeWrapper(nodeId);
        val newNodeId = alfrescoService.restoreNode(nodeWrapper.getNode(), restorePermissions);
        alfrescoService.restoreNodeContent(newNodeId, getNodeContent(nodeId));
        nodeWrapper.setRestored(true);
        mongoNodeRepository.save(nodeWrapper);
        return newNodeId;
    }

    /**
     * Performs a consistency check by comparing the cryptographic hashes of a node's content retrieved from both Alfresco and MongoDB storage systems
     * using the specified algorithm. If the hashes match, the method logs a successful comparison. If a mismatch is detected, a {@link HashesMismatchException}
     * is thrown. Any exception that occurs during hash computation is wrapped and rethrown as a {@link VaultException}.
     *
     * @param nodeId the unique identifier of the node whose content will be checked
     * @throws HashesMismatchException if the computed hashes from Alfresco and MongoDB do not match
     * @throws VaultException          if any error occurs during hash calculation or comparison
     */
    public void doubleCheck(String nodeId) {
        log.debug("Comparing {} hashes for node: {}", DOUBLE_CHECK_ALGORITHM, nodeId);
        try {
            val alfrescoHash = alfrescoService.computeHash(nodeId, DOUBLE_CHECK_ALGORITHM);
            val mongoHash = gridFsRepository.computeHash(nodeId, DOUBLE_CHECK_ALGORITHM);
            if (alfrescoHash.equals(mongoHash)) {
                log.debug("Digest check passed for node: {}", nodeId);
            } else {
                throw new HashesMismatchException(alfrescoHash, mongoHash);
            }
        } catch (Exception e) {
            throw new VaultException(String.format("Cannot compute hashes: %s", e.getMessage()));
        }
    }

}