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

import org.saidone.model.NodeWrapper;

/**
 * Service abstraction for persisting and retrieving node metadata within the
 * vault. Implementations typically store {@link NodeWrapper} instances in a
 * backing store such as MongoDB.
 */
public interface NodeService {

    /**
     * Persists the given wrapped node metadata.
     *
     * @param node the node wrapper to store
     */
    void save(NodeWrapper node);

    /**
     * Retrieves a node wrapper by its identifier.
     *
     * @param nodeId the Alfresco node identifier
     * @return the stored {@link NodeWrapper}
     */
    NodeWrapper findById(String nodeId);

    /**
     * Retrieves all stored node wrappers.
     *
     * @return iterable collection of {@link NodeWrapper}
     */
    Iterable<NodeWrapper> findAll();

    /**
     * Retrieves all node wrappers having the given notarization transaction ID.
     * A {@code null} transaction ID is used to select nodes that have not yet
     * been notarized.
     *
     * @param txId the notarization transaction ID to filter by
     * @return iterable collection of {@link NodeWrapper}
     */
    Iterable<NodeWrapper> findByTxId(String txId);

    /**
     * Removes the stored node metadata identified by the given ID.
     *
     * @param nodeId the Alfresco node identifier
     */
    void deleteById(String nodeId);

}
