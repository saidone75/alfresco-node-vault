/*
 * Alfresco Node Vault - ad aeternam documentorum conservationem
 * Copyright (C) 2025-2026 Saidone
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

package org.saidone.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.core.model.Node;
import org.saidone.component.BaseComponent;
import org.saidone.exception.HashesMismatchException;
import org.saidone.exception.NodeNotFoundOnAlfrescoException;
import org.saidone.exception.NodeNotFoundOnVaultException;
import org.saidone.exception.NodeRestoreInProgressException;
import org.saidone.exception.VaultException;
import org.saidone.misc.ProgressTrackingInputStream;
import org.saidone.model.dto.NodeWrapperDto;
import org.saidone.service.content.ContentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    @Value("${application.service.vault.delete-on-alfresco}")
    private boolean deleteOnAlfresco;

    @Value("${application.service.vault.delete-node.restore-on-alfresco}")
    private boolean restoreOnAlfrescoOnDelete;

    @Value("${application.service.vault.delete-node.delete-on-alfresco}")
    private boolean deleteOnAlfrescoOnDelete;

    private static final String DOUBLE_CHECK_ALGORITHM = "MD5";

    // IDs of the nodes being restored, to reject concurrent restores of the same node
    private final Set<String> restoresInProgress = ConcurrentHashMap.newKeySet();

    /**
     * Archives a node by its ID.
     * <p>
     * Retrieves the node and its content from Alfresco, stores metadata and
     * binaries through the configured services with checksum information,
     * optionally verifies the checksum and finally deletes the source node
     * from Alfresco.
     * </p>
     * <p>
     * A node already stored in the vault is never overwritten: only the
     * optional deletion on Alfresco is performed, after verifying that the
     * Alfresco content still matches the vault copy when double check is enabled.
     * </p>
     *
     * @param nodeId the ID of the node to archive
     * @throws NodeNotFoundOnAlfrescoException if the node is not found in Alfresco
     * @throws VaultException                  if any error occurs during archiving, including rollback
     */
    public void archiveNode(String nodeId) {
        log.info("Archiving node: {}", nodeId);
        if (!nodeService.existsById(nodeId)) {
            storeNode(nodeId);
        } else {
            log.info("Node {} is already in the vault, skipping storage", nodeId);
            if (!alfrescoService.nodeExists(nodeId)) {
                throw new NodeNotFoundOnAlfrescoException(nodeId);
            }
            // do not delete the node on Alfresco if its content differs from the vault copy
            if (deleteOnAlfresco && doubleCheck) doubleCheck(nodeId);
        }
        if (deleteOnAlfresco) deleteNodeOnAlfresco(nodeId);
    }

    /**
     * Stores node metadata and content in the vault, rolling back any partially
     * persisted data on failure.
     *
     * @param nodeId the ID of the node to store
     * @throws NodeNotFoundOnAlfrescoException if the node is not found in Alfresco
     * @throws VaultException                  if any error occurs during storage
     */
    private void storeNode(String nodeId) {
        try {
            val node = alfrescoService.getNode(nodeId);
            val nodeContentInputStream = new ProgressTrackingInputStream(
                    alfrescoService.getNodeContent(nodeId),
                    nodeId,
                    node.getContent().getSizeInBytes());
            contentService.archiveNodeContent(node, nodeContentInputStream);
            val nodeWrapper = new NodeWrapperDto(node);
            // record the content key version so that key rotation can find the node even when metadata is not encrypted
            val contentInfo = contentService.getNodeContentInfo(nodeId);
            if (contentInfo.isEncrypted()) {
                nodeWrapper.setKeyVersion(contentInfo.getKeyVersion());
            }
            nodeService.save(nodeWrapper);
            if (doubleCheck) doubleCheck(nodeId);
        } catch (FeignException.NotFound e) {
            throw new NodeNotFoundOnAlfrescoException(nodeId);
        } catch (Exception e) {
            log.trace(e.getMessage(), e);
            rollback(nodeId);
            throw new VaultException(String.format("Error archiving node %s: %s", nodeId, e.getMessage()));
        }
    }

    /**
     * Deletes an archived node on Alfresco.
     * <p>
     * At this point the vault copy is complete, so a failure must never trigger
     * a rollback: the deletion may have succeeded even if the call failed (e.g. on
     * a timeout). The error is logged and the deletion is retried on the next
     * archiving run, since the node still matches the archiving query.
     * </p>
     *
     * @param nodeId the ID of the archived node
     */
    private void deleteNodeOnAlfresco(String nodeId) {
        try {
            alfrescoService.deleteNode(nodeId);
        } catch (Exception e) {
            log.trace(e.getMessage(), e);
            log.error("Node {} archived but not deleted on Alfresco: {}", nodeId, e.getMessage());
        }
    }

    /**
     * Removes any partially persisted data for the provided node.
     * <p>
     * If an error occurs during archiving, this method is invoked to clean up
     * both the stored binary content and the node metadata entry in the vault.
     * Any exception thrown by the underlying services is logged but otherwise
     * ignored to avoid masking the original failure.
     * </p>
     *
     * @param nodeId identifier of the node being rolled back
     */
    private void rollback(String nodeId) {
        log.debug("Rollback required for node: {}", nodeId);
        try {
            contentService.deleteNodeContent(nodeId);
            nodeService.deleteById(nodeId);
        } catch (Exception e) {
            log.trace(e.getMessage(), e);
            log.debug("Rollback failed for node: {}", nodeId);
        }
    }

    /**
     * Retrieves the wrapped node metadata from the vault by node ID.
     *
     * @param nodeId the ID of the node
     * @return the {@link NodeWrapperDto} containing node metadata
     * @throws NodeNotFoundOnVaultException if the node is not found in the vault
     */
    private NodeWrapperDto getNodeWrapper(String nodeId) {
        return nodeService.findById(nodeId);
    }

    /**
     * Retrieves the node metadata by node ID.
     *
     * @param nodeId the ID of the node
     * @return the Alfresco Node object
     * @throws NodeNotFoundOnVaultException if the node is not found in the vault
     * @throws VaultException               if the stored metadata cannot be parsed
     */
    public Node getNode(String nodeId) throws VaultException {
        return getNodeWrapper(nodeId).getNode();
    }

    /**
     * Restores a node from the vault back to Alfresco.
     * <p>
     * Restores node metadata and content, optionally restoring permissions,
     * and records the ID of the restored node in the vault.
     * </p>
     * <p>
     * The operation is idempotent: if the node created by a previous restore
     * still exists in Alfresco, its ID is returned and no new node is created
     * (e.g. when a client retries after a timeout). A node whose restored copy
     * has been deleted from Alfresco can be restored again.
     * </p>
     *
     * @param nodeId             the ID of the node to restore
     * @param restorePermissions whether to restore permissions along with the node
     * @return the ID of the node restored in Alfresco
     * @throws NodeNotFoundOnVaultException    if the node is not found in the vault
     * @throws NodeRestoreInProgressException  if a restore of the same node is already running
     * @throws VaultException                  if stored metadata cannot be parsed
     */
    public String restoreNode(String nodeId, boolean restorePermissions) throws VaultException {
        if (!restoresInProgress.add(nodeId)) {
            throw new NodeRestoreInProgressException(nodeId);
        }
        try {
            val nodeWrapper = getNodeWrapper(nodeId);
            if (restoredNodeExists(nodeWrapper)) {
                log.info("Node {} already restored as {}", nodeId, nodeWrapper.getRestoredNodeId());
                return nodeWrapper.getRestoredNodeId();
            }
            val newNodeId = alfrescoService.restoreNode(nodeWrapper.getNode(), restorePermissions);
            alfrescoService.restoreNodeContent(newNodeId, contentService.getNodeContent(nodeId));
            nodeWrapper.setRestoredNodeId(newNodeId);
            nodeService.save(nodeWrapper);
            return newNodeId;
        } finally {
            restoresInProgress.remove(nodeId);
        }
    }

    /**
     * Checks whether the node created by the last restore still exists in Alfresco.
     *
     * @param nodeWrapper the vault node
     * @return {@code true} if a restored copy of the node exists in Alfresco
     */
    private boolean restoredNodeExists(NodeWrapperDto nodeWrapper) {
        return nodeWrapper.getRestoredNodeId() != null && alfrescoService.nodeExists(nodeWrapper.getRestoredNodeId());
    }

    /**
     * Deletes a node from the vault and optionally performs Alfresco side operations first.
     * <p>
     * Default behaviour:
     * <ol>
     *     <li>Restore node in Alfresco</li>
     *     <li>Delete restored node in Alfresco (move to trash)</li>
     *     <li>Delete node metadata and content from vault</li>
     * </ol>
     * If the original node or a restored copy of it already exists in Alfresco,
     * restore and delete steps are skipped.
     * </p>
     *
     * @param nodeId identifier of the node to delete
     */
    public void deleteNode(String nodeId) {
        val nodeWrapper = getNodeWrapper(nodeId);
        val node = nodeWrapper.getNode();
        if (!alfrescoService.nodeExists(nodeId) && !restoredNodeExists(nodeWrapper)) {
            String restoredNodeId = null;
            if (restoreOnAlfrescoOnDelete) {
                restoredNodeId = restoreNode(nodeId, false);
            }
            if (deleteOnAlfrescoOnDelete) {
                alfrescoService.deleteNode(restoredNodeId != null ? restoredNodeId : node.getId(), false);
            }
        } else {
            log.info("Node {} or its restored copy already exists in Alfresco, skipping restore/delete-on-Alfresco steps.", nodeId);
        }
        contentService.deleteNodeContent(nodeId);
        nodeService.deleteById(nodeId);
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
