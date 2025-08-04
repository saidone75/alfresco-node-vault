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

package org.saidone.repository;

import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.component.BaseComponent;
import org.saidone.model.NodeWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Repository implementation for managing NodeWrapper entities in MongoDB.
 * <p>
 * Implements the MongoRepository interface using MongoOperations to perform database operations.
 * <p>
 * Activation of this repository is controlled by the property 'application.service.vault.encryption.enabled':
 * it is enabled when the value is "false" or the property is missing.
 * <p>
 * Offers various CRUD operations such as insertion, save, batch operations, find by example, find by sort
 * or pageable criteria, counting, and removal of entities. Supports custom queries using Example, Sort, and Pageable.
 * <p>
 * The findBy(Example, Function) method is not implemented and will always throw an UnsupportedOperationException.
 * <p>
 * This class extends BaseComponent and is intended for scenarios where node encryption is disabled at the application level.
 * <p>
 * Depends on MongoOperations for thread-safe persistence handling of NodeWrapper documents.
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.service.vault.encryption.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class MongoNodeRepositoryImpl extends BaseComponent implements MongoRepository<NodeWrapper, String> {

    private final MongoOperations mongoOperations;

    @PostConstruct
    @Override
    public void init() {
        super.init();
        try {
            mongoOperations.getCollectionNames();
        } catch (Exception e) {
            log.error("Unable to start {}", this.getClass().getSimpleName());
            super.shutDown(0);
        }
    }

    @Override
    @NonNull
    public <S extends NodeWrapper> S insert(@NonNull S entity) {
        mongoOperations.insert(entity);
        return entity;
    }

    @Override
    @NonNull
    public <S extends NodeWrapper> List<S> insert(@NonNull Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        for (S entity : entities) {
            result.add(insert(entity));
        }
        return result;
    }

    @Override
    @NonNull
    public <S extends NodeWrapper> S save(@NonNull S entity) {
        mongoOperations.save(entity);
        return entity;
    }

    @Override
    @NonNull
    public <S extends NodeWrapper> List<S> saveAll(@NonNull Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        for (S entity : entities) {
            result.add(save(entity));
        }
        return result;
    }

    @Override
    @NonNull
    public <S extends NodeWrapper> Optional<S> findOne(@NonNull Example<S> example) {
        return Optional.ofNullable(mongoOperations.findOne(
                Query.query(Criteria.byExample(example)),
                example.getProbeType()));
    }

    @Override
    @NonNull
    public <S extends NodeWrapper> List<S> findAll(@NonNull Example<S> example) {
        return mongoOperations.find(
                Query.query(Criteria.byExample(example)),
                example.getProbeType());
    }

    @Override
    @NonNull
    public <S extends NodeWrapper> List<S> findAll(@NonNull Example<S> example, @NonNull Sort sort) {
        return mongoOperations.find(
                Query.query(Criteria.byExample(example)).with(sort),
                example.getProbeType());
    }

    @Override
    @NonNull
    public <S extends NodeWrapper> Page<S> findAll(@NonNull Example<S> example, @NonNull Pageable pageable) {
        long count = count(example);
        val content = mongoOperations.find(
                Query.query(Criteria.byExample(example)).with(pageable),
                example.getProbeType());
        return new PageImpl<>(content, pageable, count);
    }

    @Override
    public <S extends NodeWrapper, R> @NonNull R findBy(@NonNull Example<S> example, @NonNull Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
        val methodSignature = StackWalker.getInstance()
                .walk(Stream::findFirst)
                .map(StackWalker.StackFrame::toString)
                .orElse("unknown");
        throw new UnsupportedOperationException(String.format("Method %s is not implemented in this version of the repository", methodSignature));
    }

    @Override
    @NonNull
    public Optional<NodeWrapper> findById(@NonNull String id) {
        return Optional.ofNullable(mongoOperations.findById(id, NodeWrapper.class));
    }

    @Override
    public boolean existsById(@NonNull String id) {
        return mongoOperations.exists(Query.query(Criteria.where("_id").is(id)), NodeWrapper.class);
    }

    @Override
    @NonNull
    public List<NodeWrapper> findAll() {
        return mongoOperations.findAll(NodeWrapper.class);
    }

    /**
     * Retrieves node wrappers by notarization transaction ID. A {@code null} value
     * matches nodes without a transaction ID.
     *
     * @param ntx the transaction ID to filter by
     * @return list of matching nodes
     */
    public List<NodeWrapper> findByNtx(String ntx) {
        val query = new Query(Criteria.where("ntx").is(ntx));
        return mongoOperations.find(query, NodeWrapper.class);
    }

    // find by encryption key version
    public List<NodeWrapper> findByKv(String kv) {
        val query = new Query(Criteria.where("kv").is(kv));
        return mongoOperations.find(query, NodeWrapper.class);
    }

    @Override
    @NonNull
    public List<NodeWrapper> findAllById(@NonNull Iterable<String> ids) {
        val query = new Query(Criteria.where("_id").in(ids));
        return mongoOperations.find(query, NodeWrapper.class);
    }

    @Override
    public long count() {
        return mongoOperations.count(new Query(), NodeWrapper.class);
    }

    @Override
    public void deleteById(@NonNull String id) {
        val query = new Query(Criteria.where("_id").is(id));
        mongoOperations.remove(query, NodeWrapper.class);
    }

    @Override
    public void delete(@NonNull NodeWrapper entity) {
        mongoOperations.remove(entity);
    }

    @Override
    public void deleteAllById(@NonNull Iterable<? extends String> ids) {
        val query = new Query(Criteria.where("_id").in(ids));
        mongoOperations.remove(query, NodeWrapper.class);
    }

    @Override
    public void deleteAll(@NonNull Iterable<? extends NodeWrapper> entities) {
        for (val entity : entities) {
            delete(entity);
        }
    }

    @Override
    public void deleteAll() {
        mongoOperations.remove(new Query(), NodeWrapper.class);
    }

    @Override
    @NonNull
    public List<NodeWrapper> findAll(@NonNull Sort sort) {
        return mongoOperations.find(new Query().with(sort), NodeWrapper.class);
    }

    @Override
    @NonNull
    public Page<NodeWrapper> findAll(@NonNull Pageable pageable) {
        long count = count();
        val content = mongoOperations.find(
                new Query().with(pageable),
                NodeWrapper.class);

        return new PageImpl<>(content, pageable, count);
    }

    @Override
    public <S extends NodeWrapper> long count(@NonNull Example<S> example) {
        return mongoOperations.count(Query.query(Criteria.byExample(example)), example.getProbeType());
    }

    @Override
    public <S extends NodeWrapper> boolean exists(@NonNull Example<S> example) {
        return mongoOperations.exists(Query.query(Criteria.byExample(example)), example.getProbeType());
    }

}