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

package org.saidone.service.locking;

import lombok.val;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.saidone.config.LockingProperties;
import org.saidone.exception.NodeOperationInProgressException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/** Coordinates node mutations and background jobs through the shared MongoDB lock provider. */
@Component
@RequiredArgsConstructor
@Slf4j
public class VaultOperationCoordinator {

    private final LockProvider lockProvider;
    private final LockingProperties properties;

    /** Rejects concurrent operations on the same node, irrespective of operation type. */
    public <T> T withNodeLock(String nodeId, Supplier<T> action) {
        val configuration = new LockConfiguration(ClockProvider.now(), "node:" + nodeId,
                properties.nodeLockAtMostFor(), Duration.ZERO);
        val lock = lockProvider.lock(configuration)
                .orElseThrow(() -> new NodeOperationInProgressException(nodeId));
        return execute(lock, action);
    }

    public void withNodeLock(String nodeId, Runnable action) {
        withNodeLock(nodeId, () -> {
            action.run();
            return null;
        });
    }

    /** Returns null when the job is already locked; skipped runs are not queued. */
    public <T> T runJob(String name, Supplier<T> action) {
        val configuration = new LockConfiguration(ClockProvider.now(), "job:" + name,
                properties.jobLockAtMostFor(), properties.jobLockAtLeastFor());
        val lock = lockProvider.lock(configuration);
        if (lock.isEmpty()) {
            log.info("Skipping job {} because its lock is already held", name);
            return null;
        }
        return execute(lock.get(), action);
    }

    public void runJob(String name, Runnable action) {
        runJob(name, () -> {
            action.run();
            return null;
        });
    }

    private <T> T execute(SimpleLock lock, Supplier<T> action) {
        // try-with-resources preserves the action's failure if releasing the lock also fails.
        try (val acquired = new AcquiredLock(lock)) {
            return action.get();
        }
    }

    private record AcquiredLock(SimpleLock lock) implements AutoCloseable {
        @Override
        public void close() {
            lock.unlock();
        }
    }
}
