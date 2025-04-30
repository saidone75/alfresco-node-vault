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

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.core.model.Node;
import org.apache.logging.log4j.util.Strings;
import org.saidone.component.BaseComponent;
import org.saidone.exception.HashesMismatchException;
import org.saidone.exception.NodeNotOnVaultException;
import org.saidone.exception.VaultException;
import org.saidone.model.MetadataKeys;
import org.saidone.model.NodeContent;
import org.saidone.model.NodeWrapper;
import org.saidone.repository.GridFsRepositoryImpl;
import org.saidone.repository.MongoNodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;

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
    private final MongoNodeRepository mongoNodeRepository;
    private final GridFsRepositoryImpl gridFsRepository;

    @Value("${application.service.vault.hash-algorithm}")
    private String checksumAlgorithm;

    @Value("${application.service.vault.double-check}")
    private boolean doubleCheck;
    private static final String DOUBLE_CHECK_ALGORITHM = "MD5";

    /**
     * Archives a node by its ID.
     * <p>
     * Retrieves the node and its content from Alfresco, saves metadata to MongoDB,
     * stores the content in GridFS with checksum metadata, optionally performs a double-check
     * of the checksum, and finally deletes the node from Alfresco.
     * </p>
     *
     * @param nodeId the ID of the node to archive
     * @throws NodeNotOnVaultException if the node is not found in Alfresco
     * @throws VaultException          if any error occurs during archiving, including rollback
     */
    public void archiveNode(String nodeId) {
        log.info("Archiving node: {}", nodeId);
        try {
            val node = alfrescoService.getNode(nodeId);
            val file = alfrescoService.getNodeContent(nodeId);
            mongoNodeRepository.save(new NodeWrapper(node));
            try (val is = new FileInputStream(file)) {
                val metadata = new HashMap<String, String>() {{
                    put(MetadataKeys.UUID, nodeId);
                }};
                if (Strings.isNotBlank(checksumAlgorithm)) {
                    metadata.put(MetadataKeys.CHECKSUM_ALGORITHM, checksumAlgorithm);
                    metadata.put(MetadataKeys.CHECKSUM_VALUE, computeHash(file, checksumAlgorithm));
                }
                gridFsRepository.saveFile(is, node.getName(), node.getContent().getMimeType(), metadata);
            }
            Files.deleteIfExists(file.toPath());
            if (doubleCheck) doubleCheck(nodeId);
            alfrescoService.deleteNode(nodeId);
        } catch (FeignException.NotFound e) {
            throw new NodeNotOnVaultException(nodeId);
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
     * @throws NodeNotOnVaultException if the node is not found in the vault
     */
    private NodeWrapper getNodeWrapper(String nodeId) {
        val nodeOptional = mongoNodeRepository.findById(nodeId);
        if (nodeOptional.isPresent()) {
            val nodeWrapper = nodeOptional.get();
            return nodeWrapper;
        } else {
            throw new NodeNotOnVaultException(nodeId);
        }
    }

    /**
     * Retrieves the node metadata by node ID.
     *
     * @param nodeId the ID of the node
     * @return the Alfresco Node object
     * @throws NodeNotOnVaultException if the node is not found in the vault
     */
    public Node getNode(String nodeId) {
        return getNodeWrapper(nodeId).getNode();
    }

    /**
     * Retrieves the content of a node stored in GridFS by node ID.
     *
     * @param nodeId the ID of the node
     * @return the NodeContent containing file name, content type, length, and content stream
     * @throws NodeNotOnVaultException if the node content is not found in the vault
     */
    public NodeContent getNodeContent(String nodeId) {
        val gridFSFile = gridFsRepository.findFileById(nodeId);
        if (gridFSFile == null) {
            throw new NodeNotOnVaultException(nodeId);
        }
        val nodeContent = new NodeContent();
        nodeContent.setFileName(gridFSFile.getFilename());
        if (gridFSFile.getMetadata() != null && gridFSFile.getMetadata().containsKey("_contentType")) {
            nodeContent.setContentType(gridFSFile.getMetadata().getString("_contentType"));
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
     * @throws NodeNotOnVaultException if the node is not found in the vault
     */
    public String restoreNode(String nodeId, boolean restorePermissions) {
        val nodeWrapper = getNodeWrapper(nodeId);
        val newNodeId = alfrescoService.restoreNode(nodeWrapper.getNode(), restorePermissions);
        alfrescoService.restoreNodeContent(newNodeId, getNodeContent(nodeId));
        nodeWrapper.setRestored(true);
        mongoNodeRepository.save(nodeWrapper);
        return newNodeId;
    }

    /**
     * Computes the hash of a file using the specified algorithm.
     *
     * @param file the file to compute the hash for
     * @param hash the name of the hash algorithm (e.g., "MD5", "SHA-256")
     * @return the hexadecimal string representation of the computed hash
     * @throws IOException              if an I/O error occurs reading the file
     * @throws NoSuchAlgorithmException if the specified hash algorithm is not available
     */
    public static String computeHash(File file, String hash) throws IOException, NoSuchAlgorithmException {
        val digest = MessageDigest.getInstance(hash);
        try (val fis = new FileInputStream(file)) {
            byte[] byteArray = new byte[8192];
            int bytesCount;
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Performs double-check by comparing the MD5 hash of the node content
     * retrieved from Alfresco and the one computed on MongoDB.
     * <p>
     * Throws a {@link HashesMismatchException} if the hashes do not match.
     * </p>
     *
     * @param nodeId the ID of the node to double-check
     * @throws VaultException if hash computation fails or hashes mismatch
     */
    public void doubleCheck(String nodeId) {
        log.debug("Comparing {} hashes for node: {}", DOUBLE_CHECK_ALGORITHM, nodeId);
        File file = null;
        String alfrescoHash;
        String mongoHash;
        try {
            file = alfrescoService.getNodeContent(nodeId);
            alfrescoHash = computeHash(file, DOUBLE_CHECK_ALGORITHM);
            mongoHash = gridFsRepository.computeHash(nodeId, DOUBLE_CHECK_ALGORITHM);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new VaultException(String.format("Cannot compute hashes: %s", e.getMessage()));
        } finally {
            try {
                if (file != null) Files.deleteIfExists(file.toPath());
            } catch (IOException e) {
                log.warn(e.getMessage());
            }
        }
        if (alfrescoHash.equals(mongoHash)) {
            log.debug("Digest check passed for node: {}", nodeId);
        } else {
            throw new HashesMismatchException(alfrescoHash, mongoHash);
        }
    }

}