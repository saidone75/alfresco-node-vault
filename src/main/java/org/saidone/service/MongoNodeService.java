/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
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

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import org.saidone.component.BaseComponent;
import org.saidone.exception.NodeNotFoundOnVaultException;
import org.saidone.model.mapper.NodeMapper;
import org.saidone.model.dto.NodeWrapperDto;
import org.saidone.repository.MongoNodeRepositoryImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Default {@link NodeService} implementation that persists node metadata using
 * a {@link MongoNodeRepositoryImpl}. It throws
 * {@link NodeNotFoundOnVaultException} when a requested node is not present in
 * the repository.
 */
@RequiredArgsConstructor
@Service
public class MongoNodeService extends BaseComponent implements NodeService {

    /** Repository used for persisting and retrieving node metadata. */
    private final MongoNodeRepositoryImpl mongoNodeRepository;
    /** Mapper used to translate between DTO and persistence entity. */
    private final NodeMapper nodeMapper;

    /**
     * {@inheritDoc}
     *
     * <p>This implementation simply delegates to
     * {@link MongoNodeRepositoryImpl#save(Object)} after mapping the DTO to
     * the persistence entity.</p>
     */
    @Override
    @SneakyThrows
    public void save(NodeWrapperDto nodeWrapper) {
        mongoNodeRepository.save(nodeMapper.toEntity(nodeWrapper));
    }

    /**
     * Retrieves the stored node wrapper by its identifier.
     *
     * @param nodeId the Alfresco node identifier
     * @return the stored {@link NodeWrapperDto}
     * @throws NodeNotFoundOnVaultException if the node does not exist
     */
    @Override
    public NodeWrapperDto findById(String nodeId) {
        val nodeOptional = mongoNodeRepository.findById(nodeId).map(nodeMapper::toDto);
        if (nodeOptional.isPresent()) {
            return nodeOptional.get();
        } else {
            throw new NodeNotFoundOnVaultException(nodeId);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Page<NodeWrapperDto> findByArchiveDateRange(Instant from, Instant to, Pageable pageable) {
        return mongoNodeRepository.findByArchiveDateRange(from, to, pageable).map(nodeMapper::toDto);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation delegates to
     * {@link MongoNodeRepositoryImpl#findNotarized(Pageable)}.</p>
     */
    @Override
    public Page<NodeWrapperDto> findNotarized(Pageable pageable) {
        return mongoNodeRepository.findNotarized(pageable).map(nodeMapper::toDto);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<NodeWrapperDto> findByNtx(String ntx) {
        return nodeMapper.toDtoList(mongoNodeRepository.findByNtx(ntx));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<NodeWrapperDto> findByKv(int kv) {
        return nodeMapper.toDtoList(mongoNodeRepository.findByKv(kv));
    }

    /**
     * {@inheritDoc}
     *
     * <p>The returned iterable is backed by the underlying repository and
     * reflects the current state of the vault.</p>
     */
    @Override
    public Iterable<NodeWrapperDto> findAll() {
        return nodeMapper.toDtoList(mongoNodeRepository.findAll());
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
