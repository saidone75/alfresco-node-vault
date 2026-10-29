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

package org.saidone.repository;

import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bson.Document;
import org.saidone.component.BaseComponent;
import org.saidone.exception.InvalidRequestException;
import org.saidone.model.MetadataKeys;
import org.saidone.model.dto.NodeSearchRequestDto;
import org.saidone.model.entity.NodeEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Repository implementation for managing {@link NodeEntity} entities in MongoDB.
 *
 * <p>Implements the {@link MongoRepository} interface using {@link MongoOperations}
 * to perform database operations.</p>
 *
 * <p>Activation of this repository is controlled by the
 * {@code application.service.vault.encryption.metadata.enabled} property: the bean is
 * instantiated when the value is {@code false} or the property is missing
 * (otherwise {@link EncryptedMongoNodeRepositoryImpl} is used instead).</p>
 *
 * <p>Provides CRUD operations including insertion, saving, batch operations, and
 * queries by {@link Example}, {@link Sort} or {@link Pageable}. It also supports
 * counting and removal of entities.</p>
 *
 * <p>The {@code findBy(Example, Function)} method is not implemented and always
 * throws an {@link UnsupportedOperationException}.</p>
 *
 * <p>This class extends {@link BaseComponent} and is intended for scenarios
 * where node metadata encryption is disabled at the application level. It relies on
 * {@link MongoOperations} for thread-safe persistence of {@link NodeEntity}
 * documents.</p>
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.service.vault.encryption.metadata.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class MongoNodeRepositoryImpl extends BaseComponent implements MongoRepository<NodeEntity, String> {
    /**
     * Template for executing MongoDB operations. It provides low level access to
     * the database and is used throughout this repository for persistence and
     * queries.
     */
    private final MongoOperations mongoOperations;

    /** Field holding the node as a BSON document. */
    private static final String NODE_DOCUMENT = "nb";

    /**
     * {@inheritDoc}
     * <p>
     * The method verifies that the MongoDB connection is available at start-up.
     * If the connection cannot be established the application is shut down to
     * avoid running in an inconsistent state.
     * </p>
     */
    @PostConstruct
    @Override
    public void init() {
        super.init();
        try {
            mongoOperations.getCollectionNames();
        } catch (Exception e) {
            log.error("Unable to start {}", this.getClass().getSimpleName());
            super.shutDown(1);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <S extends NodeEntity> S insert(@NonNull S entity) {
        mongoOperations.insert(entity);
        return entity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <S extends NodeEntity> List<S> insert(@NonNull Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        entities.forEach(entity -> result.add(insert(entity)));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <S extends NodeEntity> S save(@NonNull S entity) {
        mongoOperations.save(entity);
        return entity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <S extends NodeEntity> List<S> saveAll(@NonNull Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        entities.forEach(entity -> result.add(save(entity)));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <S extends NodeEntity> Optional<S> findOne(@NonNull Example<S> example) {
        return Optional.ofNullable(mongoOperations.findOne(
                Query.query(Criteria.byExample(example)),
                example.getProbeType()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <S extends NodeEntity> List<S> findAll(@NonNull Example<S> example) {
        return mongoOperations.find(
                Query.query(Criteria.byExample(example)),
                example.getProbeType());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <S extends NodeEntity> List<S> findAll(@NonNull Example<S> example, @NonNull Sort sort) {
        return mongoOperations.find(
                Query.query(Criteria.byExample(example)).with(sort),
                example.getProbeType());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <S extends NodeEntity> Page<S> findAll(@NonNull Example<S> example, @NonNull Pageable pageable) {
        long count = count(example);
        val content = mongoOperations.find(
                Query.query(Criteria.byExample(example)).with(pageable),
                example.getProbeType());
        return new PageImpl<>(content, pageable, count);
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException always thrown as this method is not implemented
     */
    @Override
    public <S extends NodeEntity, R> @NonNull R findBy(@NonNull Example<S> example, @NonNull Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
        val methodSignature = StackWalker.getInstance()
                .walk(Stream::findFirst)
                .map(StackWalker.StackFrame::toString)
                .orElse("unknown");
        throw new UnsupportedOperationException(String.format("Method %s is not implemented in this version of the repository", methodSignature));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Optional<NodeEntity> findById(@NonNull String id) {
        return Optional.ofNullable(mongoOperations.findById(id, NodeEntity.class));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean existsById(@NonNull String id) {
        return mongoOperations.exists(Query.query(Criteria.where("_id").is(id)), NodeEntity.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public List<NodeEntity> findAll() {
        return mongoOperations.findAll(NodeEntity.class);
    }

    /**
     * Retrieves node wrappers archived within the specified date range using pagination.
     *
     * <p>When both {@code from} and {@code to} are {@code null}, this method
     * delegates to {@link #findAll(Pageable)}.</p>
     *
     * <p>If the supplied {@link Pageable} does not define a sort order, results
     * are ordered by archive date in ascending order.</p>
     *
     * @param from     lower bound of the archive date range, inclusive. {@code null} for no lower bound.
     * @param to       upper bound of the archive date range, inclusive. {@code null} for no upper bound.
     * @param pageable pagination information
     * @return page of matching nodes
     */
    public Page<NodeEntity> findByArchiveDateRange(Instant from, Instant to, Pageable pageable) {
        val criteria = Criteria.where("adt").gte(from).lte(to);
        long count = mongoOperations.count(new Query(criteria), NodeEntity.class);
        val query = new Query(criteria).with(pageable);
        val content = mongoOperations.find(query, NodeEntity.class);
        return new PageImpl<>(content, pageable, count);
    }


    /**
     * Retrieves notarized node wrappers where the transaction id is not null.
     *
     * @param pageable pagination information
     * @return page of notarized nodes
     */
    public Page<NodeEntity> findNotarized(Pageable pageable) {
        val criteria = Criteria.where(MetadataKeys.NOTARIZATION_TRANSACTION_ID).ne(null);
        val query = new Query(criteria).with(pageable);
        long count = mongoOperations.count(new Query(criteria), NodeEntity.class);
        val content = mongoOperations.find(query, NodeEntity.class);
        return new PageImpl<>(content, pageable, count);
    }

    /**
     * Counts notarized nodes (nodes with non-null notarization transaction id).
     *
     * @return number of notarized nodes
     */
    public long countNotarized() {
        val criteria = Criteria.where(MetadataKeys.NOTARIZATION_TRANSACTION_ID).ne(null);
        return mongoOperations.count(new Query(criteria), NodeEntity.class);
    }

    /**
     * Retrieves a random sample of notarized nodes.
     *
     * @param size maximum number of nodes to sample
     * @return random list of notarized nodes
     */
    public List<NodeEntity> findNotarizedRandom(int size) {
        int sampleSize = Math.max(size, 0);
        if (sampleSize == 0) {
            return List.of();
        }
        val criteria = Criteria.where(MetadataKeys.NOTARIZATION_TRANSACTION_ID).ne(null);
        val aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.sample(sampleSize)
        );
        return mongoOperations.aggregate(aggregation, NodeEntity.class, NodeEntity.class).getMappedResults();
    }

    /**
     * Retrieves a page of node IDs by notarization transaction ID, in ascending
     * ID order. A {@code null} value matches nodes without a transaction ID.
     *
     * @param ntx     the transaction ID to filter by
     * @param afterId only IDs greater than this one are returned; {@code null} to start from the first
     * @param limit   maximum number of IDs to return
     * @return the matching node IDs
     */
    public List<String> findIdsByNtx(String ntx, String afterId, int limit) {
        return findIds(Criteria.where(MetadataKeys.NOTARIZATION_TRANSACTION_ID).is(ntx), afterId, limit);
    }

    /**
     * Retrieves a page of node IDs by encryption key version, in ascending ID
     * order.
     *
     * @param kv      the encryption key version to filter by
     * @param afterId only IDs greater than this one are returned; {@code null} to start from the first
     * @param limit   maximum number of IDs to return
     * @return the IDs of nodes encrypted with the specified key version
     */
    public List<String> findIdsByKv(int kv, String afterId, int limit) {
        return findIds(Criteria.where(MetadataKeys.KEY_VERSION).is(kv), afterId, limit);
    }

    /**
     * Retrieves a page of IDs of the nodes matching the given criteria, loading
     * only the {@code _id} field of each document.
     */
    private List<String> findIds(Criteria criteria, String afterId, int limit) {
        val query = new Query(afterId == null ? criteria : new Criteria().andOperator(criteria, Criteria.where("_id").gt(afterId)))
                .with(Sort.by(Sort.Direction.ASC, "_id"))
                .limit(limit);
        query.fields().include("_id");
        return mongoOperations.find(query, NodeEntity.class).stream().map(NodeEntity::getId).toList();
    }

    /**
     * Searches nodes by their metadata using pagination. All the supplied
     * criteria must match. Only nodes stored as BSON documents (not encrypted)
     * are taken into account.
     *
     * @param request  search criteria
     * @param pageable pagination information
     * @return page of matching nodes
     */
    public Page<NodeEntity> search(NodeSearchRequestDto request, Pageable pageable) {
        val criteria = new ArrayList<Criteria>();
        criteria.add(Criteria.where(NODE_DOCUMENT).exists(true));
        if (request.getName() != null) {
            criteria.add(Criteria.where(nodeField("name")).regex(wildcardToRegex(request.getName()), "i"));
        }
        if (request.getNodeType() != null) {
            criteria.add(Criteria.where(nodeField("nodeType")).is(request.getNodeType()));
        }
        addDateRange(criteria, nodeField("createdAt"), request.getCreatedFrom(), request.getCreatedTo());
        addDateRange(criteria, nodeField("modifiedAt"), request.getModifiedFrom(), request.getModifiedTo());
        if (request.getMimeType() != null) {
            criteria.add(Criteria.where(nodeField("content.mimeType")).is(request.getMimeType()));
        }
        if (request.getMinSize() != null || request.getMaxSize() != null) {
            val size = Criteria.where(nodeField("content.sizeInBytes"));
            if (request.getMinSize() != null) size.gte(request.getMinSize());
            if (request.getMaxSize() != null) size.lte(request.getMaxSize());
            criteria.add(size);
        }
        if (request.getAspects() != null && !request.getAspects().isEmpty()) {
            criteria.add(Criteria.where(nodeField("aspectNames")).all(request.getAspects()));
        }
        if (request.getProperties() != null && !request.getProperties().isEmpty()) {
            val propertyCriteria = request.getProperties().stream().map(MongoNodeRepositoryImpl::propertyCriteria).toList();
            criteria.add(request.getPropertiesMatch() == NodeSearchRequestDto.Match.ANY
                    ? new Criteria().orOperator(propertyCriteria)
                    : new Criteria().andOperator(propertyCriteria));
        }
        val filter = new Criteria().andOperator(criteria);
        long count = mongoOperations.count(new Query(filter), NodeEntity.class);
        val content = mongoOperations.find(new Query(filter).with(pageable), NodeEntity.class);
        return new PageImpl<>(content, pageable, count);
    }

    /**
     * Builds the condition on a single node property.
     *
     * @param filter property condition
     * @return criteria matching the property values
     */
    private static Criteria propertyCriteria(NodeSearchRequestDto.PropertyFilter filter) {
        if (filter.getName() == null || filter.getValues() == null || filter.getValues().isEmpty()) {
            throw new InvalidRequestException("Property filters require a name and at least one value");
        }
        val property = Criteria.where(nodeField(String.format("properties.%s", filter.getName())));
        return filter.getMatch() == NodeSearchRequestDto.Match.ALL
                ? property.all(filter.getValues())
                : property.in(filter.getValues());
    }

    /**
     * Adds an inclusive range condition on a date stored as an ISO-8601 string.
     * Dates are converted with {@code $dateFromString} because their string
     * representation is not suitable for lexicographic comparison.
     *
     * @param criteria list the condition is added to
     * @param field    path of the date field
     * @param from     lower bound, inclusive. {@code null} for no lower bound.
     * @param to       upper bound, inclusive. {@code null} for no upper bound.
     */
    private static void addDateRange(List<Criteria> criteria, String field, Instant from, Instant to) {
        if (from != null) criteria.add(Criteria.expr(compareDate("$gte", field, from)));
        if (to != null) criteria.add(Criteria.expr(compareDate("$lte", field, to)));
    }

    /**
     * Builds an expression comparing a date stored as a string with the given instant.
     */
    private static AggregationExpression compareDate(String operator, String field, Instant value) {
        val date = new Document("$dateFromString", new Document("dateString", String.format("$%s", field)).append("onError", null));
        return context -> new Document(operator, List.of(date, Date.from(value)));
    }

    /**
     * Returns the path of a field of the node BSON document.
     *
     * @param path path of the field inside the node
     * @return full path of the field
     */
    private static String nodeField(String path) {
        return String.format("%s.%s", NODE_DOCUMENT, path);
    }

    /**
     * Converts a name pattern with {@code *} and {@code ?} wildcards into an
     * anchored regular expression, quoting everything else.
     */
    private static String wildcardToRegex(String pattern) {
        val regex = new StringBuilder("^");
        val literal = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            if (c == '*' || c == '?') {
                if (!literal.isEmpty()) {
                    regex.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                regex.append(c == '*' ? ".*" : ".");
            } else {
                literal.append(c);
            }
        }
        if (!literal.isEmpty()) regex.append(Pattern.quote(literal.toString()));
        return regex.append('$').toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public List<NodeEntity> findAllById(@NonNull Iterable<String> ids) {
        val query = new Query(Criteria.where("_id").in(ids));
        return mongoOperations.find(query, NodeEntity.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long count() {
        return mongoOperations.count(new Query(), NodeEntity.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteById(@NonNull String id) {
        val query = new Query(Criteria.where("_id").is(id));
        mongoOperations.remove(query, NodeEntity.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(@NonNull NodeEntity entity) {
        mongoOperations.remove(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAllById(@NonNull Iterable<? extends String> ids) {
        val query = new Query(Criteria.where("_id").in(ids));
        mongoOperations.remove(query, NodeEntity.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAll(@NonNull Iterable<? extends NodeEntity> entities) {
        for (val entity : entities) {
            delete(entity);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAll() {
        mongoOperations.remove(new Query(), NodeEntity.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public List<NodeEntity> findAll(@NonNull Sort sort) {
        return mongoOperations.find(new Query().with(sort), NodeEntity.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Page<NodeEntity> findAll(@NonNull Pageable pageable) {
        long count = count();
        val content = mongoOperations.find(
                new Query().with(pageable),
                NodeEntity.class);

        return new PageImpl<>(content, pageable, count);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <S extends NodeEntity> long count(@NonNull Example<S> example) {
        return mongoOperations.count(Query.query(Criteria.byExample(example)), example.getProbeType());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <S extends NodeEntity> boolean exists(@NonNull Example<S> example) {
        return mongoOperations.exists(Query.query(Criteria.byExample(example)), example.getProbeType());
    }

}
