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

package org.saidone.service.notarization;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.logging.log4j.util.Strings;
import org.saidone.component.BaseComponent;
import org.saidone.exception.NotarizationException;
import org.saidone.service.NodeService;
import org.saidone.service.content.ContentService;
import org.springframework.beans.factory.annotation.Value;

/**
 * Base implementation for services performing document notarization.
 *
 * <p>This component provides the common logic for computing document hashes
 * and updating nodes after the hash has been persisted.</p>
 */
@RequiredArgsConstructor
@Slf4j
public abstract class AbstractNotarizationService extends BaseComponent implements NotarizationService {

    private final NodeService nodeService;
    private final ContentService contentService;

    @Value("${application.service.vault.hash-algorithm}")
    private String checksumAlgorithm;

    /**
     * Persists the given hash.
     *
     * @param nodeId the node identifier
     * @param hash   the hash value to store
     * @return an implementation specific transaction id
     */
    public abstract String putHash(String nodeId, String hash);

    /**
     * Retrieves the stored hash for the given transaction id.
     *
     * @param txId the transaction identifier
     * @return the stored hash value
     */
    public abstract String getHash(String txId);

    /**
     * Computes the hash of the node content and stores it through {@link #putHash}.
     *
     * @param nodeId the identifier of the node to notarize
     */
    @SneakyThrows
    public void notarizeNode(String nodeId) {
        log.debug("Notarizing document {}", nodeId);
        val hash = contentService.computeHash(nodeId, checksumAlgorithm);
        val txHash = putHash(nodeId, hash);
        val nodeWrapper = nodeService.findById(nodeId);
        nodeWrapper.setNotarizationTxId(txHash);
        nodeService.save(nodeWrapper);
    }

    /**
     * Validates that the notarization for the specified node is still valid.
     *
     * <p>The method retrieves the stored transaction id from the node,
     * fetches the hash back from the notarization system and compares it with
     * the hash computed from the current content.</p>
     *
     * @param nodeId the identifier of the node to check
     * @throws NotarizationException if the node is not notarized or hashes do not match
     */
    @SneakyThrows
    public void checkNotarization(String nodeId) {
        log.debug("Checking notarization for document {}", nodeId);
        val notarizationTransactionId = nodeService.findById(nodeId).getNotarizationTxId();
        if (Strings.isBlank(notarizationTransactionId)) {
            throw new NotarizationException(String.format("Node %s is not notarized", nodeId));
        }
        boolean hashesMatch = getHash(notarizationTransactionId).equals(contentService.computeHash(nodeId, checksumAlgorithm));
        if (!hashesMatch) {
            throw new NotarizationException(String.format("Node %s is notarized but hashes do not match.", nodeId));
        }
    }

}
