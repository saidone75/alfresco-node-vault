/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
 * Copyright (C) 2025 Saidone
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

import org.saidone.exception.NodeNotFoundOnVaultException;
import org.saidone.model.NodeWrapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/**
 * Service abstraction for persisting and retrieving node metadata within the
 * vault. Implementations typically store {@link NodeWrapper} instances in a
 * backing store such as MongoDB.
 *
 * <p>The API is intentionally minimal and focused on CRUD-style operations.
 * Higher level orchestration of archive and restore flows is handled by
 * {@link org.saidone.service.VaultService}.</p>
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
     * @throws NodeNotFoundOnVaultException if the node does not exist in the vault
     */
    NodeWrapper findById(String nodeId);

    /**
     * Retrieves node wrappers archived within the specified date range using pagination.
     * Both bounds are inclusive. Passing {@code null} for one of the parameters will
     * make the range open-ended on that side.
     *
     * @param from     the lower bound of the archive date range, inclusive
     * @param to       the upper bound of the archive date range, inclusive
     * @param pageable pagination information
     * @return page of {@link NodeWrapper}
     */
    Page<NodeWrapper> findByArchiveDateRange(Instant from, Instant to, Pageable pageable);

    /**
     * Retrieves all node wrappers having the given notarization transaction ID.
     * A {@code null} transaction ID is used to select nodes that have not yet
     * been notarized.
     *
     * @param ntx the notarization transaction ID to filter by
     * @return iterable collection of {@link NodeWrapper}
     */
    Iterable<NodeWrapper> findByNtx(String ntx);

    /**
     * Retrieves all node wrappers associated with the specified encryption key version.
     *
     * @param kv the encryption key version to filter by
     * @return iterable collection of {@link NodeWrapper}
     */
    Iterable<NodeWrapper> findByKv(int kv);

    /**
     * Retrieves all stored node wrappers.
     *
     * @return iterable collection of {@link NodeWrapper}
     */
    Iterable<NodeWrapper> findAll();

    /**
     * Removes the stored node metadata identified by the given ID.
     *
     * @param nodeId the Alfresco node identifier
     */
    void deleteById(String nodeId);

}
