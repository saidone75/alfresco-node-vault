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

import org.saidone.component.BaseComponent;
import org.saidone.model.NodeWrapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Repository
@ConditionalOnProperty(name = "application.service.vault.encryption.metadata", havingValue = "false", matchIfMissing = true)
public class MongoNodeRepositoryImpl extends BaseComponent implements MongoNodeRepository {

    private final MongoRepository<NodeWrapper, String> delegate;

    public MongoNodeRepositoryImpl(@Qualifier("mongoNodeRepository") MongoRepository<NodeWrapper, String> delegate) {
        this.delegate = delegate;
    }

    @Override
    public <S extends NodeWrapper> S insert(S entity) {
        return delegate.insert(entity);
    }

    @Override
    public <S extends NodeWrapper> List<S> insert(Iterable<S> entities) {
        return delegate.insert(entities);
    }

    @Override
    public <S extends NodeWrapper> S save(S entity) {
        return delegate.save(entity);
    }

    @Override
    public <S extends NodeWrapper> List<S> saveAll(Iterable<S> entities) {
        return delegate.saveAll(entities);
    }

    @Override
    public <S extends NodeWrapper> Optional<S> findOne(Example<S> example) {
        return delegate.findOne(example);
    }

    @Override
    public <S extends NodeWrapper> List<S> findAll(Example<S> example) {
        return delegate.findAll(example);
    }

    @Override
    public <S extends NodeWrapper> List<S> findAll(Example<S> example, Sort sort) {
        return delegate.findAll(example, sort);
    }

    @Override
    public <S extends NodeWrapper> Page<S> findAll(Example<S> example, Pageable pageable) {
        return delegate.findAll(example, pageable);
    }

    @Override
    public <S extends NodeWrapper, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
        return delegate.findBy(example, queryFunction);
    }

    @Override
    public Optional<NodeWrapper> findById(String s) {
        return delegate.findById(s);
    }

    @Override
    public List<NodeWrapper> findAll() {
        return delegate.findAll();
    }

    @Override
    public List<NodeWrapper> findAllById(Iterable<String> strings) {
        return delegate.findAllById(strings);
    }

    @Override
    public List<NodeWrapper> findAll(Sort sort) {
        return delegate.findAll(sort);
    }

    @Override
    public Page<NodeWrapper> findAll(Pageable pageable) {
        return delegate.findAll(pageable);
    }

    @Override
    public <S extends NodeWrapper> long count(Example<S> example) {
        return delegate.count(example);
    }

    @Override
    public <S extends NodeWrapper> boolean exists(Example<S> example) {
        return delegate.exists(example);
    }

    @Override
    public boolean existsById(String s) {
        return delegate.existsById(s);
    }

    @Override
    public long count() {
        return delegate.count();
    }

    @Override
    public void deleteById(String s) {
        delegate.deleteById(s);
    }

    @Override
    public void delete(NodeWrapper entity) {
        delegate.delete(entity);
    }

    @Override
    public void deleteAllById(Iterable<? extends String> strings) {
        delegate.deleteAllById(strings);
    }

    @Override
    public void deleteAll(Iterable<? extends NodeWrapper> entities) {
        delegate.deleteAll(entities);
    }

    @Override
    public void deleteAll() {
        delegate.deleteAll();
    }

}