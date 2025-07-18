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

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import org.saidone.component.BaseComponent;
import org.saidone.exception.NodeNotFoundOnVaultException;
import org.saidone.model.NodeWrapper;
import org.saidone.repository.MongoNodeRepositoryImpl;
import org.springframework.stereotype.Service;

/**
 * Default {@link NodeService} implementation that persists node metadata using
 * a {@link MongoNodeRepositoryImpl}. It throws
 * {@link NodeNotFoundOnVaultException} when a requested node is not present in
 * the repository.
 */
@RequiredArgsConstructor
@Service
public class MongoNodeService extends BaseComponent implements NodeService {

    private final MongoNodeRepositoryImpl mongoNodeRepository;

    /**
     * Saves the given node wrapper to the repository.
     *
     * @param nodeWrapper node metadata to persist
     */
    @Override
    @SneakyThrows
    public void save(NodeWrapper nodeWrapper) {
        mongoNodeRepository.save(nodeWrapper);
    }

    /**
     * Retrieves the stored node wrapper by its identifier.
     *
     * @param nodeId the Alfresco node identifier
     * @return the stored {@link NodeWrapper}
     * @throws NodeNotFoundOnVaultException if the node does not exist
     */
    @Override
    public NodeWrapper findById(String nodeId) {
        val nodeOptional = mongoNodeRepository.findById(nodeId);
        if (nodeOptional.isPresent()) {
            return nodeOptional.get();
        } else {
            throw new NodeNotFoundOnVaultException(nodeId);
        }
    }

    @Override
    public Iterable<NodeWrapper> findAll() {
        return mongoNodeRepository.findAll();
    }

    @Override
    public Iterable<NodeWrapper> findByTxId(String txId) {
        return mongoNodeRepository.findByTxId(txId);
    }

    /**
     * Deletes the node wrapper identified by the given ID.
     *
     * @param nodeId the Alfresco node identifier
     */
    @Override
    public void deleteById(String nodeId) {
        mongoNodeRepository.deleteById(nodeId);
    }
}