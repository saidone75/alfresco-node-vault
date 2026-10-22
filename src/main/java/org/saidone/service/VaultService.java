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

package org.saidone.service;

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
import org.saidone.exception.NodeOperationInProgressException;
import org.saidone.exception.VaultException;
import org.saidone.misc.ProgressTrackingInputStream;
import org.saidone.model.dto.NodeWrapperDto;
import org.saidone.monitor.VaultOperationMetrics;
import org.saidone.monitor.VaultOperationMetrics.Operation;
import org.saidone.monitor.VaultOperationMetrics.Outcome;
import org.saidone.service.content.ContentService;
import org.saidone.service.locking.VaultOperationCoordinator;
import org.saidone.service.locking.AlfrescoArchiveLockService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

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
    private final VaultOperationCoordinator operationCoordinator;
    private final VaultOperationMetrics operationMetrics;
    private final AlfrescoArchiveLockService alfrescoArchiveLockService;

    @Value("${application.service.vault.double-check}")
    private boolean doubleCheck;

    @Value("${application.service.vault.delete-on-alfresco}")
    private boolean deleteOnAlfresco;

    @Value("${application.service.vault.delete-node.restore-on-alfresco}")
    private boolean restoreOnAlfrescoOnDelete;

    @Value("${application.service.vault.delete-node.delete-on-alfresco}")
    private boolean deleteOnAlfrescoOnDelete;

    @Value("${application.service.vault.hash-algorithm:SHA-256}")
    private String checksumAlgorithm;

    /**
     * Archives a node by its ID.
     * <p>
     * Retrieves the node and its content from Alfresco, stores metadata and
     * binaries through the configured services with checksum information,
     * optionally double checks the stored copy and finally deletes the source
     * node from Alfresco.
     * </p>
     * <p>
     * A node already stored in the vault is never overwritten: only the
     * optional deletion on Alfresco is performed, after verifying that the
     * Alfresco content still matches the checksum of the vault copy. Metadata is
     * saved only after the optional double check, so an existing entry always
     * refers to a copy that passed it.
     * </p>
     *
     * @param nodeId the ID of the node to archive
     * @throws NodeNotFoundOnAlfrescoException if the node is not found in Alfresco
     * @throws VaultException                  if any error occurs during archiving, including rollback
     */
    public void archiveNode(String nodeId) {
        val archivedBytes = withOperationMetrics(Operation.ARCHIVE,
                () -> operationCoordinator.withNodeLock(nodeId, () -> doArchiveNode(nodeId)));
        operationMetrics.recordArchivedBytes(archivedBytes);
    }

    private long doArchiveNode(String nodeId) {
        try (val sourceLock = alfrescoArchiveLockService.acquire(nodeId)) {
            return doArchiveNode(nodeId, sourceLock);
        }
    }

    private long doArchiveNode(String nodeId, AlfrescoArchiveLockService.Lease sourceLock) {
        long archivedBytes = 0;
        log.info("Archiving node: {}", nodeId);
        if (!nodeService.existsById(nodeId)) {
            archivedBytes = storeNode(nodeId, sourceLock);
        } else {
            log.info("Node {} is already in the vault, skipping storage", nodeId);
            if (!alfrescoService.nodeExists(nodeId)) {
                throw new NodeNotFoundOnAlfrescoException(nodeId);
            }
            // Always verify an existing vault copy before deleting, since Alfresco content may have changed since it was stored.
            if (deleteOnAlfresco) verifyAlfrescoContent(nodeId);
        }
        sourceLock.assertValid();
        if (deleteOnAlfresco) sourceLock.delete(() -> deleteNodeOnAlfresco(nodeId));
        return archivedBytes;
    }

    /**
     * Stores node metadata and content in the vault, rolling back any partially
     * persisted data on failure.
     *
     * @param nodeId the ID of the node to store
     * @return original content size in bytes after successful storage and verification
     * @throws NodeNotFoundOnAlfrescoException if the node is not found in Alfresco
     * @throws VaultException                  if any error occurs during storage
     */
    private long storeNode(String nodeId, AlfrescoArchiveLockService.Lease sourceLock) {
        try {
            val node = alfrescoService.getNode(nodeId);
            sourceLock.stripLockMetadata(node);
            val nodeContentInputStream = new ProgressTrackingInputStream(
                    alfrescoService.getNodeContent(nodeId),
                    nodeId,
                    node.getContent().getSizeInBytes());
            contentService.archiveNodeContent(node, nodeContentInputStream);
            // A truncated download would otherwise be stored with a consistent checksum.
            if (nodeContentInputStream.getBytesRead() != node.getContent().getSizeInBytes()) {
                throw new VaultException(String.format("Content size mismatch for node %s: expected %d bytes, received %d",
                        nodeId, node.getContent().getSizeInBytes(), nodeContentInputStream.getBytesRead()));
            }
            val nodeWrapper = new NodeWrapperDto(node);
            // Record the content key version so that key rotation can find the node even when metadata is not encrypted.
            val contentInfo = contentService.getNodeContentInfo(nodeId);
            if (contentInfo.isEncrypted()) {
                nodeWrapper.setKeyVersion(contentInfo.getKeyVersion());
            }
            // Check before saving metadata: once saved, the node counts as archived and is never checked again.
            if (doubleCheck) doubleCheck(nodeId);
            sourceLock.assertValid();
            nodeService.save(nodeWrapper);
            return node.getContent().getSizeInBytes();
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
    private boolean deleteNodeOnAlfresco(String nodeId) {
        try {
            alfrescoService.deleteNode(nodeId);
            return true;
        } catch (Exception e) {
            log.trace(e.getMessage(), e);
            log.error("Node {} archived but not deleted on Alfresco: {}", nodeId, e.getMessage());
            return false;
        }
    }

    /**
     * Removes any partially persisted data for the provided node.
     * <p>
     * If an error occurs during archiving, this method is invoked to clean up
     * both the node metadata entry and the stored binary content in the vault.
     * Any exception thrown by the underlying services is logged but otherwise
     * ignored to avoid masking the original failure.
     * </p>
     * <p>
     * Each step runs on its own, so a failure in one does not skip the other.
     * Content left behind is overwritten when the node is archived again.
     * </p>
     *
     * @param nodeId identifier of the node being rolled back
     */
    private void rollback(String nodeId) {
        log.debug("Rollback required for node: {}", nodeId);
        try {
            nodeService.deleteById(nodeId);
        } catch (Exception e) {
            log.trace(e.getMessage(), e);
            log.error("Rollback failed to delete metadata for node {}: {}", nodeId, e.getMessage());
        }
        try {
            contentService.deleteNodeContent(nodeId);
        } catch (Exception e) {
            log.trace(e.getMessage(), e);
            log.error("Rollback failed to delete content for node {}: {}", nodeId, e.getMessage());
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
     * <p>
     * If uploading content or saving the restored node ID fails, the newly created
     * node is permanently deleted on a best-effort basis and the original error is
     * rethrown. A process interruption, a lost creation response or failed cleanup
     * may still require manual reconciliation before retrying.
     * </p>
     *
     * @param nodeId             the ID of the node to restore
     * @param restorePermissions whether to restore permissions along with the node
     * @return the ID of the node restored in Alfresco
     * @throws NodeNotFoundOnVaultException    if the node is not found in the vault
     * @throws NodeOperationInProgressException if another operation on the same node is already running
     * @throws VaultException                  if stored metadata cannot be parsed
     */
    public String restoreNode(String nodeId, boolean restorePermissions) throws VaultException {
        return withOperationMetrics(Operation.RESTORE,
                () -> operationCoordinator.withNodeLock(nodeId, () -> doRestoreNode(nodeId, restorePermissions)));
    }

    @SneakyThrows
    private String doRestoreNode(String nodeId, boolean restorePermissions) {
        val nodeWrapper = getNodeWrapper(nodeId);
        if (restoredNodeExists(nodeWrapper)) {
            log.info("Node {} already restored as {}", nodeId, nodeWrapper.getRestoredNodeId());
            return nodeWrapper.getRestoredNodeId();
        }
        val newNodeId = alfrescoService.restoreNode(nodeWrapper.getNode(), restorePermissions);
        try {
            alfrescoService.restoreNodeContent(newNodeId, contentService.getNodeContent(nodeId));
            nodeWrapper.setRestoredNodeId(newNodeId);
            nodeService.save(nodeWrapper);
        } catch (Exception e) {
            try {
                alfrescoService.deleteNode(newNodeId, true);
            } catch (Exception cleanupException) {
                e.addSuppressed(cleanupException);
                log.error("Could not remove failed restore {} for node {}", newNodeId, nodeId, cleanupException);
            }
            throw e;
        }
        return newNodeId;
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
        withOperationMetrics(Operation.DELETE, () -> {
            operationCoordinator.withNodeLock(nodeId, () -> doDeleteNode(nodeId));
            return null;
        });
    }

    private void doDeleteNode(String nodeId) {
        val nodeWrapper = getNodeWrapper(nodeId);
        val node = nodeWrapper.getNode();
        if (!alfrescoService.nodeExists(nodeId) && !restoredNodeExists(nodeWrapper)) {
            String restoredNodeId = null;
            if (restoreOnAlfrescoOnDelete) {
                restoredNodeId = doRestoreNode(nodeId, false);
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

    /** Measures public requests once; internal restore steps remain part of their parent operation. */
    private <T> T withOperationMetrics(Operation operation, Supplier<T> action) {
        if (!operationMetrics.isEnabled()) return action.get();
        val startedAt = System.nanoTime();
        var outcome = Outcome.ERROR;
        try {
            val result = action.get();
            outcome = Outcome.SUCCESS;
            return result;
        } catch (NodeOperationInProgressException e) {
            outcome = Outcome.SKIPPED;
            throw e;
        } finally {
            operationMetrics.recordOperation(operation, outcome, System.nanoTime() - startedAt);
        }
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
        log.debug("Comparing {} hashes for node: {}", checksumAlgorithm, nodeId);
        try {
            val alfrescoHash = alfrescoService.computeHash(nodeId, checksumAlgorithm);
            val vaultHash = contentService.computeHash(nodeId, checksumAlgorithm);
            if (alfrescoHash.equals(vaultHash)) {
                log.debug("Digest check passed for node: {}", nodeId);
            } else {
                throw new HashesMismatchException(alfrescoHash, vaultHash);
            }
        } catch (Exception e) {
            throw new VaultException(String.format("Error while checking hashes: %s", e.getMessage()));
        }
    }

    /**
     * Verifies that the current Alfresco content matches the checksum recorded
     * when the vault copy was stored, downloading only from Alfresco. Content
     * stored without a recorded checksum falls back to {@link #doubleCheck(String)}.
     *
     * @param nodeId the unique identifier of the node whose content will be checked
     * @throws VaultException if the Alfresco content differs from the vault copy or cannot be read
     */
    private void verifyAlfrescoContent(String nodeId) {
        val contentInfo = contentService.getNodeContentInfo(nodeId);
        val algorithm = contentInfo.getContentHashAlgorithm();
        val vaultHash = contentInfo.getContentHash();
        if (algorithm == null || vaultHash == null) {
            doubleCheck(nodeId);
            return;
        }
        log.debug("Comparing Alfresco content with stored {} checksum for node: {}", algorithm, nodeId);
        try {
            val alfrescoHash = alfrescoService.computeHash(nodeId, algorithm);
            if (!alfrescoHash.equals(vaultHash)) {
                throw new HashesMismatchException(alfrescoHash, vaultHash);
            }
        } catch (Exception e) {
            throw new VaultException(String.format("Error while checking hashes: %s", e.getMessage()));
        }
    }

}
