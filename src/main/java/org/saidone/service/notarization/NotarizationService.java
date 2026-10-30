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

import org.saidone.model.dto.NodeWrapperDto;

/**
 * Contract for components able to notarize documents.
 *
 * <p>
 *     Implementations are responsible for persisting hashes of a node's
 *     content to an external store (for example a blockchain) and for
 *     retrieving them afterwards. {@link AbstractNotarizationService} provides
 *     a base implementation of the common logic.
 * </p>
 */

public interface NotarizationService {

    /**
     * Computes and stores the hash for the given node.
     *
     * @param nodeId the node whose content should be notarized
     */
    void notarizeNode(String nodeId);

    /**
     * Verifies that the stored hash for the given node matches the hash of its
     * current content.
     *
     * @param nodeId the node whose notarization should be validated
     * @throws org.saidone.exception.NotarizationException if the node is not
     *                                                     notarized or hashes do not match
     */
    void checkNotarization(String nodeId);

    /**
     * Verifies the notarization of a node against a hash of its content
     * computed by the caller, so that the content is not read again. The
     * caller must hold the node lock.
     *
     * @param nodeWrapper the node, with its metadata and notarization data
     * @param contentHash the hash of the node content, computed with the
     *                    algorithm recorded with the metadata hash
     * @throws org.saidone.exception.NotarizationException if the node is not
     *                                                     notarized or hashes do not match
     */
    void verifyNotarization(NodeWrapperDto nodeWrapper, String contentHash);

}
