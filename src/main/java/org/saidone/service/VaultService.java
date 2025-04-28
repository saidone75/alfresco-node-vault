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

import com.mongodb.client.MongoClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.core.model.Node;
import org.apache.logging.log4j.util.Strings;
import org.saidone.component.BaseComponent;
import org.saidone.exception.ArchiveNodeException;
import org.saidone.exception.NodeNotFoundOnVaultException;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class VaultService extends BaseComponent {

    private final AlfrescoService alfrescoService;
    private final MongoNodeRepository mongoNodeRepository;
    private final GridFsRepositoryImpl gridFsRepository;
    private final MongoClient mongoClient;

    @Value("${application.service.vault.hash-algorithm}")
    private String checksumAlgorithm;

    @Value("${application.service.vault.double-check-algorithm}")
    private String doubleCheckAlgorithm;

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
                    metadata.put(MetadataKeys.CHECKSUM_VALUE, computeDigest(file, checksumAlgorithm));
                }
                gridFsRepository.saveFile(is, node.getName(), node.getContent().getMimeType(), metadata);
            }
            Files.deleteIfExists(file.toPath());
            if (Strings.isNotBlank(doubleCheckAlgorithm)) doubleCheck(nodeId);
            alfrescoService.deleteNode(nodeId);
        } catch (Exception e) {
            log.trace(e.getMessage(), e);
            // rollback
            log.debug("Rollback required for node: {}", nodeId);
            mongoNodeRepository.deleteById(nodeId);
            gridFsRepository.deleteFileById(nodeId);
            throw new ArchiveNodeException(String.format("Error archiving node %s: %s", nodeId, e.getMessage()));
        }
    }

    public Node getNode(String nodeId) {
        val nodeOptional = mongoNodeRepository.findById(nodeId);
        if (nodeOptional.isPresent()) {
            val nodeWrapper = nodeOptional.get();
            return nodeWrapper.getNode();
        } else {
            throw new NodeNotFoundOnVaultException(nodeId);
        }
    }

    public NodeContent getNodeContent(String nodeId) {
        val gridFSFile = gridFsRepository.findFileById(nodeId);
        if (gridFSFile == null) {
            throw new NodeNotFoundOnVaultException(nodeId);
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

    public static String computeDigest(File file, String hash) throws IOException, NoSuchAlgorithmException {
        val digest = MessageDigest.getInstance(hash);
        try (val fis = new FileInputStream(file)) {
            byte[] byteArray = new byte[8192];
            int bytesCount;
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
        }
        byte[] bytes = digest.digest();
        val sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void doubleCheck(String nodeId) {
        log.debug("Comparing {} digest for node: {}", doubleCheckAlgorithm, nodeId);
        File file = null;
        String alfrescoDigest;
        String mongoDigest;
        try {
            file = alfrescoService.getNodeContent(nodeId);
            alfrescoDigest = computeDigest(file, doubleCheckAlgorithm);
            log.trace("Alfresco digest for node {}: {}", nodeId, alfrescoDigest);
            mongoDigest = gridFsRepository.calculateMd5(nodeId);
            log.trace("MongoDB digest for node {}: {}", nodeId, mongoDigest);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new ArchiveNodeException("Cannot compute hashes");
        } finally {
            try {
                if (file != null) Files.deleteIfExists(file.toPath());
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        }
        if (alfrescoDigest.equals(mongoDigest)) {
            log.debug("Digest check passed for node: {}", nodeId);
        } else {
            throw new ArchiveNodeException("Hashes mismatch");
        }
    }

}