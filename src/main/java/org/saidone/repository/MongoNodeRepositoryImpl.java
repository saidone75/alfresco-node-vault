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

package org.saidone.repository;

import lombok.RequiredArgsConstructor;
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

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.service.vault.encryption.metadata", havingValue = "false", matchIfMissing = true)
public class MongoNodeRepositoryImpl extends BaseComponent implements MongoRepository<NodeWrapper, String> {

    private final MongoOperations mongoOperations;

    @Override
    public <S extends NodeWrapper> S insert(S entity) {
        mongoOperations.insert(entity);
        return entity;
    }

    @Override
    public <S extends NodeWrapper> List<S> insert(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        for (S entity : entities) {
            result.add(insert(entity));
        }
        return result;
    }

    @Override
    public <S extends NodeWrapper> S save(S entity) {
        mongoOperations.save(entity);
        return entity;
    }

    @Override
    public <S extends NodeWrapper> List<S> saveAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        for (S entity : entities) {
            result.add(save(entity));
        }
        return result;
    }

    @Override
    public <S extends NodeWrapper> Optional<S> findOne(Example<S> example) {
        return Optional.ofNullable(mongoOperations.findOne(
                Query.query(Criteria.byExample(example)),
                example.getProbeType()));
    }

    @Override
    public <S extends NodeWrapper> List<S> findAll(Example<S> example) {
        return mongoOperations.find(
                Query.query(Criteria.byExample(example)),
                example.getProbeType());
    }

    @Override
    public <S extends NodeWrapper> List<S> findAll(Example<S> example, Sort sort) {
        return mongoOperations.find(
                Query.query(Criteria.byExample(example)).with(sort),
                example.getProbeType());
    }

    @Override
    public <S extends NodeWrapper> Page<S> findAll(Example<S> example, Pageable pageable) {
        long count = count(example);
        val content = mongoOperations.find(
                Query.query(Criteria.byExample(example)).with(pageable),
                example.getProbeType());
        return new PageImpl<>(content, pageable, count);
    }

    @Override
    public <S extends NodeWrapper, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
        return null;
    }

    @Override
    public Optional<NodeWrapper> findById(String id) {
        return Optional.ofNullable(mongoOperations.findById(id, NodeWrapper.class));
    }

    @Override
    public boolean existsById(String id) {
        return mongoOperations.exists(Query.query(Criteria.where("_id").is(id)), NodeWrapper.class);
    }

    @Override
    public List<NodeWrapper> findAll() {
        return mongoOperations.findAll(NodeWrapper.class);
    }

    @Override
    public List<NodeWrapper> findAllById(Iterable<String> ids) {
        val query = new Query(Criteria.where("_id").in(ids));
        return mongoOperations.find(query, NodeWrapper.class);
    }

    @Override
    public long count() {
        return mongoOperations.count(new Query(), NodeWrapper.class);
    }

    @Override
    public void deleteById(String id) {
        val query = new Query(Criteria.where("_id").is(id));
        mongoOperations.remove(query, NodeWrapper.class);
    }

    @Override
    public void delete(NodeWrapper entity) {
        mongoOperations.remove(entity);
    }

    @Override
    public void deleteAllById(Iterable<? extends String> ids) {
        val query = new Query(Criteria.where("_id").in(ids));
        mongoOperations.remove(query, NodeWrapper.class);
    }

    @Override
    public void deleteAll(Iterable<? extends NodeWrapper> entities) {
        for (val entity : entities) {
            delete(entity);
        }
    }

    @Override
    public void deleteAll() {
        mongoOperations.remove(new Query(), NodeWrapper.class);
    }

    @Override
    public List<NodeWrapper> findAll(Sort sort) {
        return mongoOperations.find(new Query().with(sort), NodeWrapper.class);
    }

    @Override
    public Page<NodeWrapper> findAll(Pageable pageable) {
        long count = count();
        val content = mongoOperations.find(
                new Query().with(pageable),
                NodeWrapper.class);

        return new PageImpl<>(content, pageable, count);
    }

    @Override
    public <S extends NodeWrapper> long count(Example<S> example) {
        return mongoOperations.count(Query.query(Criteria.byExample(example)), example.getProbeType());
    }

    @Override
    public <S extends NodeWrapper> boolean exists(Example<S> example) {
        return mongoOperations.exists(Query.query(Criteria.byExample(example)), example.getProbeType());
    }

}