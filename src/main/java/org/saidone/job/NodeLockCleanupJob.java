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

package org.saidone.job;

import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.javacrumbs.shedlock.core.ClockProvider;
import org.bson.Document;
import org.saidone.service.locking.VaultOperationCoordinator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.regex;

/** Removes only node locks whose last lease expired at least one hour ago. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "application.node-lock-cleanup.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class NodeLockCleanupJob {
    private final MongoCollection<Document> locks;
    private final VaultOperationCoordinator operationCoordinator;

    @Autowired
    public NodeLockCleanupJob(MongoClient mongoClient,
                              @Value("${spring.data.mongodb.database}") String database,
                              VaultOperationCoordinator operationCoordinator) {
        this(mongoClient.getDatabase(database).getCollection("shedLock")
                .withWriteConcern(WriteConcern.MAJORITY), operationCoordinator);
    }

    NodeLockCleanupJob(MongoCollection<Document> locks, VaultOperationCoordinator operationCoordinator) {
        this.locks = locks;
        this.operationCoordinator = operationCoordinator;
    }

    @Scheduled(cron = "${application.node-lock-cleanup.cron-expression:0 0 * * * ?}")
    void cleanup() {
        operationCoordinator.runJob("node-lock-cleanup", this::deleteExpiredNodeLocks);
    }

    private void deleteExpiredNodeLocks() {
        val cutoff = Date.from(ClockProvider.now().minus(Duration.ofHours(1)));
        // Keep both conditions in the delete itself: a concurrently reacquired lock
        // no longer matches. MongoLockProvider recreates deleted records by upsert.
        long deleted = locks.deleteMany(and(regex("_id", "^node:"), lte("lockUntil", cutoff)))
                .getDeletedCount();
        log.info("Node lock cleanup deleted {} locks expired at or before {}", deleted, cutoff.toInstant());
    }

}
