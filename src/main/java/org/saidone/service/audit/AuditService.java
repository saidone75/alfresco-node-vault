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

import java.time.Instant;
import java.util.List;

/**
 * Service abstraction for persisting and querying {@link AuditEntry} objects.
 * <p>
 * Implementations are responsible for storing audit entries and retrieving
 * them using optional search criteria such as type or timestamp range.
 * </p>
 */
public interface AuditService {

    /**
     * Persist the provided audit entry.
     *
     * @param auditEntry the entry to store
     */
    void saveEntry(AuditEntry auditEntry);

    /**
     * Retrieve stored audit entries.
     *
     * @param type      optional entry type to filter by
     * @param from      lower bound of the timestamp range (inclusive)
     * @param to        upper bound of the timestamp range (inclusive)
     * @param maxItems  maximum number of items to return
     * @param skipCount number of items to skip (for pagination)
     * @return list of matching audit entries ordered by timestamp descending
     */
    List<AuditEntry> findEntries(String type, Instant from, Instant to, int maxItems, int skipCount);

}
