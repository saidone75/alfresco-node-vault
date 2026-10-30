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

package org.saidone.service.notarization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.logging.log4j.util.Strings;
import org.saidone.component.BaseComponent;
import org.saidone.exception.NodeNotNotarizedException;
import org.saidone.exception.NotarizationException;
import org.saidone.exception.NotarizedHashMismatchException;
import org.saidone.model.dto.NodeWrapperDto;
import org.saidone.service.NodeService;
import org.saidone.service.locking.VaultOperationCoordinator;
import org.saidone.service.content.ContentService;
import org.saidone.utils.NodeDigestUtils;

import java.util.function.Function;

/**
 * Base implementation for services performing document notarization.
 *
 * <p>
 *   Concrete implementations only need to provide the mechanisms used to
 *   persist and read back document hashes via {@link #putHash(String, String)}
 *   and {@link #getHash(String)}. All other operations such as computing the
 *   notarized hash and updating the node metadata are handled here.
 * </p>
 * <p>
 *   The notarized hash binds the node id to the hashes of its content and
 *   metadata, computed with the algorithm recorded with the metadata hash.
 * </p>
 */
@RequiredArgsConstructor
@Slf4j
public abstract class AbstractNotarizationService extends BaseComponent implements NotarizationService {

    private final NodeService nodeService;
    private final ContentService contentService;
    private final VaultOperationCoordinator operationCoordinator;

    /**
     * Persists the given hash.
     *
     * @param nodeId the node identifier
     * @param hash   the hash value to store
     * @return an implementation specific transaction id
     */
    protected abstract String putHash(String nodeId, String hash);

    /**
     * Retrieves the stored hash for the given transaction id.
     *
     * @param txId the transaction identifier
     * @return the stored hash value
     */
    protected abstract String getHash(String txId);

    /**
     * Computes the hash of the node id, content and metadata and stores it
     * through {@link #putHash}.
     *
     * @param nodeId the identifier of the node to notarize
     * @throws NotarizationException if the metadata do not match the hash recorded at archive time
     */
    public void notarizeNode(String nodeId) {
        operationCoordinator.withNodeLock(nodeId, () -> doNotarizeNode(nodeId));
    }

    private void doNotarizeNode(String nodeId) {
        val nodeWrapper = nodeService.findById(nodeId);
        if (Strings.isNotBlank(nodeWrapper.getNotarizationTxId())) {
            log.info("Node {} is already notarized, skipping", nodeId);
            return;
        }
        // Notarizing altered metadata would certify them, so they must still match the hash recorded at archive time.
        val metadataHash = nodeWrapper.computeMetadataHash();
        if (metadataHash == null || !metadataHash.equals(nodeWrapper.getMetadataHash())) {
            throw new NotarizationException(String.format("Metadata of node %s do not match the hash recorded at archive time", nodeId));
        }
        val algorithm = nodeWrapper.getMetadataHashAlgorithm();
        val hash = NodeDigestUtils.notarizationHash(nodeId, contentService.computeHash(nodeId, algorithm), metadataHash, algorithm);
        nodeWrapper.setNotarizationTxId(putHash(nodeId, hash));
        nodeService.save(nodeWrapper);
    }

    /**
     * Validates that the notarization for the specified node is still valid.
     *
     * <p>The method retrieves the stored transaction id from the node,
     * fetches the hash back from the notarization system and compares it with
     * the hash computed from the current content and metadata.</p>
     *
     * @param nodeId the identifier of the node to check
     * @throws NodeNotNotarizedException     if the node is not notarized
     * @throws NotarizedHashMismatchException if the hashes do not match
     */
    public void checkNotarization(String nodeId) {
        operationCoordinator.withNodeLock(nodeId, () -> doCheckNotarization(nodeId));
    }

    private void doCheckNotarization(String nodeId) {
        log.debug("Checking notarization for node {}", nodeId);
        verify(nodeService.findById(nodeId), algorithm -> contentService.computeHash(nodeId, algorithm));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyNotarization(NodeWrapperDto nodeWrapper, String contentHash) {
        verify(nodeWrapper, algorithm -> contentHash);
    }

    /**
     * Compares the notarized hash with the one recomputed from the current
     * node state. Everything is recomputed: stored hashes only help to tell
     * what changed.
     *
     * @param nodeWrapper the node to verify
     * @param contentHash provides the content hash for the given algorithm, so
     *                    that the content is read only once the cheaper checks pass
     */
    private void verify(NodeWrapperDto nodeWrapper, Function<String, String> contentHash) {
        val nodeId = nodeWrapper.getId();
        if (Strings.isBlank(nodeWrapper.getNotarizationTxId())) {
            throw new NodeNotNotarizedException(nodeId);
        }
        val metadataHash = nodeWrapper.computeMetadataHash();
        if (metadataHash == null || !metadataHash.equals(nodeWrapper.getMetadataHash())) {
            throw new NotarizedHashMismatchException(nodeId, "metadata changed since archiving");
        }
        val algorithm = nodeWrapper.getMetadataHashAlgorithm();
        val actualHash = NodeDigestUtils.notarizationHash(nodeId, contentHash.apply(algorithm), metadataHash, algorithm);
        if (!getHash(nodeWrapper.getNotarizationTxId()).equals(actualHash)) {
            throw new NotarizedHashMismatchException(nodeId, "content, metadata or notarization data changed");
        }
    }

}
