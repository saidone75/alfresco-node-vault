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
/**
 * Contract for components able to notarize documents.
 *
 * <p>Implementations store document hashes in an external system such as a
 * blockchain and later retrieve them.</p>
 */

public interface NotarizationService {

    /**
     * Retrieves the hash stored within the transaction identified by the given id.
     *
     * @param txHash the transaction identifier returned by {@link #putHash(String, String)}
     * @return the persisted hash
     */
    String getHash(String txHash);

    /**
     * Stores the supplied hash in the underlying notarization system.
     *
     * @param nodeId the node identifier
     * @param hash   the hash to store
     * @return an implementation specific transaction id
     */
    String putHash(String nodeId, String hash);

    /**
     * Computes and stores the hash for the given node.
     *
     * @param nodeId the node whose content should be notarized
     */
    void notarizeNode(String nodeId);

}
