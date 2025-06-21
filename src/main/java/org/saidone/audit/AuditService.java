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

package org.saidone.audit;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.saidone.component.BaseComponent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

@Service
@RequiredArgsConstructor
/**
 * Service for persisting and retrieving {@link AuditEntry} instances.
 * <p>
 * Audit entries are stored in MongoDB via the configured {@link MongoTemplate}
 * and can be queried by type and timestamp range.
 */
public class AuditService extends BaseComponent {

    private final MongoTemplate mongoTemplate;

    public void saveEntry(Map<String, Serializable> metadata, String type) {
        val entry = new AuditEntry();
        entry.setTimestamp(Instant.now());
        entry.setMetadata(metadata);
        entry.setType(type);
        mongoTemplate.insert(entry);
    }

    public java.util.List<AuditEntry> findEntries(String type, Instant from, Instant to, Pageable pageable) {
        val criteriaList = new ArrayList<Criteria>();
        if (type != null) {
            criteriaList.add(Criteria.where("type").is(type));
        }
        if (from != null || to != null) {
            val timeCriteria = Criteria.where("timestamp");
            if (from != null) timeCriteria.gte(from);
            if (to != null) timeCriteria.lte(to);
            criteriaList.add(timeCriteria);
        }
        val query = new Query();
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }
        query.with(pageable);
        query.with(Sort.by(Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, AuditEntry.class);
    }

}
