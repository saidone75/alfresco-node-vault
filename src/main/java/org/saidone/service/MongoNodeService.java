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

@RequiredArgsConstructor
@Service
public class MongoNodeService extends BaseComponent implements NodeService {

    private final MongoNodeRepositoryImpl mongoNodeRepository;

    @Override
    @SneakyThrows
    public void save(NodeWrapper nodeWrapper) {
        mongoNodeRepository.save(nodeWrapper);
    }

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
    public void deleteById(String nodeId) {
        mongoNodeRepository.deleteById(nodeId);
    }

}