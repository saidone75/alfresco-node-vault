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

package org.saidone.service.audit;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.saidone.component.BaseComponent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for persisting and retrieving {@link AuditEntry} instances.
 *
 * <p>Audit entries are stored in MongoDB via the configured {@link MongoTemplate}
 * and can be queried by type and timestamp range.</p>
 */
@Service
@RequiredArgsConstructor
public class AuditServiceImpl extends BaseComponent implements AuditService {

    private final MongoTemplate mongoTemplate;

    /**
     * Persist the provided audit entry in MongoDB.
     *
     * @param auditEntry the entry to store
     */
    public void saveEntry(AuditEntry auditEntry) {
        mongoTemplate.insert(auditEntry);
    }

    /**
     * Retrieve audit entries filtered by type and timestamp.
     *
     * @param type      optional entry type to filter by
     * @param from      lower bound of the timestamp range (inclusive)
     * @param to        upper bound of the timestamp range (inclusive)
     * @param maxItems  maximum number of items to return
     * @param skipCount number of items to skip (for pagination)
     * @return list of matching audit entries ordered by timestamp descending
     */
    @Override
    public List<AuditEntry> findEntries(String type, Instant from, Instant to, int maxItems, int skipCount) {
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
        var pageable = Pageable.ofSize((int) maxItems).withPage((int) skipCount);
        query.with(pageable);
        query.with(Sort.by(Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, AuditEntry.class);
    }

}
