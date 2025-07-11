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
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.core.model.Node;
import org.saidone.component.BaseComponent;
import org.saidone.exception.HashesMismatchException;
import org.saidone.exception.NodeNotFoundOnAlfrescoException;
import org.saidone.exception.NodeNotFoundOnVaultException;
import org.saidone.exception.VaultException;
import org.saidone.misc.ProgressTrackingInputStream;
import org.saidone.model.NodeWrapper;
import org.saidone.service.content.ContentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service responsible for archiving, restoring and managing nodes in the vault.
 * <p>
 * It interacts with Alfresco to retrieve nodes and their binaries while
 * persisting metadata and content through the configured
 * {@link NodeService} and {@link ContentService} implementations.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VaultService extends BaseComponent {

    private final AlfrescoService alfrescoService;
    private final NodeService nodeService;
    private final ContentService contentService;

    @Value("${application.service.vault.double-check}")
    private boolean doubleCheck;

    private static final String DOUBLE_CHECK_ALGORITHM = "MD5";

    /**
     * Archives a node by its ID.
     * <p>
     * Retrieves the node and its content from Alfresco, stores metadata and
     * binaries through the configured services with checksum information,
     * optionally verifies the checksum and finally deletes the source node
     * from Alfresco.
     * </p>
     *
     * @param nodeId the ID of the node to archive
     * @throws NodeNotFoundOnAlfrescoException if the node is not found in Alfresco
     * @throws VaultException                  if any error occurs during archiving, including rollback
     */
    public void archiveNode(String nodeId) {
        log.info("Archiving node: {}", nodeId);
        try {
            val node = alfrescoService.getNode(nodeId);
            val nodeContentInputStream = new ProgressTrackingInputStream(
                    alfrescoService.getNodeContent(nodeId),
                    nodeId,
                    node.getContent().getSizeInBytes());
            val nodeContentInfo = contentService.archiveNodeContent(node, nodeContentInputStream);
            nodeService.save(new NodeWrapper(node, nodeContentInfo));
            if (doubleCheck) doubleCheck(nodeId);
            alfrescoService.deleteNode(nodeId);
        } catch (FeignException.NotFound e) {
            throw new NodeNotFoundOnAlfrescoException(nodeId);
        } catch (Exception e) {
            log.trace(e.getMessage(), e);
            // rollback
            log.debug("Rollback required for node: {}", nodeId);
            nodeService.deleteById(nodeId);
            contentService.deleteNodeContent(nodeId);
            throw new VaultException(String.format("Error archiving node %s: %s", nodeId, e.getMessage()));
        }
    }

    /**
     * Retrieves the wrapped node metadata from the vault by node ID.
     *
     * @param nodeId the ID of the node
     * @return the {@link NodeWrapper} containing node metadata
     * @throws NodeNotFoundOnVaultException if the node is not found in the vault
     */
    private NodeWrapper getNodeWrapper(String nodeId) {
        return nodeService.findById(nodeId);
    }

    /**
     * Retrieves the node metadata by node ID.
     *
     * @param nodeId the ID of the node
     * @return the Alfresco Node object
     * @throws NodeNotFoundOnVaultException if the node is not found in the vault
     * @throws JsonProcessingException      if there is an error processing the node metadata JSON
     */
    public Node getNode(String nodeId) throws JsonProcessingException {
        return getNodeWrapper(nodeId).getNode();
    }

    /**
     * Restores a node from the vault back to Alfresco.
     * <p>
     * Restores node metadata and content, optionally restoring permissions,
     * and marks the node as restored in the vault.
     * </p>
     *
     * @param nodeId             the ID of the node to restore
     * @param restorePermissions whether to restore permissions along with the node
     * @return the new node ID assigned by Alfresco after restoration
     * @throws NodeNotFoundOnVaultException if the node is not found in the vault
     * @throws JsonProcessingException      if there is an error processing the node metadata JSON
     */
    public String restoreNode(String nodeId, boolean restorePermissions) throws JsonProcessingException {
        val nodeWrapper = getNodeWrapper(nodeId);
        val newNodeId = alfrescoService.restoreNode(nodeWrapper.getNode(), restorePermissions);
        alfrescoService.restoreNodeContent(newNodeId, contentService.getNodeContent(nodeId));
        nodeWrapper.setRestored(true);
        nodeService.save(nodeWrapper);
        return newNodeId;
    }

    /**
     * Performs a consistency check by comparing the cryptographic hashes of a node's
     * content retrieved from both Alfresco and the vault storage using the specified
     * algorithm. If the hashes match, a successful comparison is logged. If a mismatch
     * is detected, a {@link HashesMismatchException} is thrown. Any exception occurring
     * during hash computation is wrapped and rethrown as a {@link VaultException}.
     *
     * @param nodeId the unique identifier of the node whose content will be checked
     * @throws HashesMismatchException if the computed hashes from Alfresco and the vault do not match
     * @throws VaultException          if any error occurs during hash calculation or comparison
     */
    public void doubleCheck(String nodeId) {
        log.debug("Comparing {} hashes for node: {}", DOUBLE_CHECK_ALGORITHM, nodeId);
        try {
            val alfrescoHash = alfrescoService.computeHash(nodeId, DOUBLE_CHECK_ALGORITHM);
            val vaultHash = contentService.computeHash(nodeId, DOUBLE_CHECK_ALGORITHM);
            if (alfrescoHash.equals(vaultHash)) {
                log.debug("Digest check passed for node: {}", nodeId);
            } else {
                throw new HashesMismatchException(alfrescoHash, vaultHash);
            }
        } catch (Exception e) {
            throw new VaultException(String.format("Error while checking hashes: %s", e.getMessage()));
        }
    }

}