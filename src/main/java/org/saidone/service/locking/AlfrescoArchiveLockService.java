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

import feign.FeignException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.core.handler.NodesApi;
import org.alfresco.core.model.Node;
import org.alfresco.core.model.NodeBodyLock;
import org.saidone.component.BaseComponent;
import org.saidone.exception.NodeOperationInProgressException;
import org.saidone.exception.NodeNotFoundOnAlfrescoException;
import org.saidone.exception.VaultException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Optional source-side lock; the Alfresco account must be dedicated to this application. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlfrescoArchiveLockService extends BaseComponent {
    private final NodesApi nodesApi;
    private final ScheduledExecutorService scheduler = newScheduler();

    @Value("${application.service.vault.alfresco-lock.enabled:false}")
    private boolean enabled;

    @Value("${application.service.vault.alfresco-lock.ttl-seconds:120}")
    private int ttlSeconds;

    @Value("${application.service.vault.alfresco-lock.lifetime:PERSISTENT}")
    private NodeBodyLock.LifetimeEnum lifetime;

    @PostConstruct
    @Override
    public void init() {
        super.init();
        if (ttlSeconds < 30) throw new IllegalArgumentException("Alfresco lock TTL must be at least 30 seconds");
    }

    private static ScheduledExecutorService newScheduler() {
        val executor = new ScheduledThreadPoolExecutor(2, runnable -> {
            val thread = new Thread(runnable, "alfresco-lock-renewal");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    public Lease acquire(String nodeId) {
        if (!enabled) return new Lease(nodeId, false);
        // Never adopt a pre-existing lock, including one owned by our own account.
        try {
            val node = Objects.requireNonNull(nodesApi.getNode(nodeId, List.of("isLocked"), null, null).getBody()).getEntry();
            if (Boolean.TRUE.equals(node.isIsLocked())) throw new NodeOperationInProgressException(nodeId);
        } catch (FeignException.NotFound e) {
            throw new NodeNotFoundOnAlfrescoException(nodeId);
        }
        val lease = new Lease(nodeId, true);
        lease.extend();
        try {
            lease.renewal = scheduler.scheduleWithFixedDelay(lease::renew, ttlSeconds / 3, ttlSeconds / 3, TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            lease.close();
            throw e;
        }
        return lease;
    }

    @PreDestroy
    @Override
    public void stop() {
        scheduler.shutdown();
        super.stop();
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public final class Lease implements AutoCloseable {
        private final String nodeId;
        private final boolean managed;
        private ScheduledFuture<?> renewal;
        private long expiresAt;
        private RuntimeException failure;
        private boolean closed;

        private void extend() {
            val started = System.nanoTime();
            try {
                nodesApi.lockNode(nodeId, new NodeBodyLock()
                        .type(NodeBodyLock.TypeEnum.ALLOW_OWNER_CHANGES)
                        .lifetime(lifetime).timeToExpire(ttlSeconds), null, null);
            } catch (FeignException.Conflict e) {
                throw new NodeOperationInProgressException(nodeId);
            } catch (FeignException.NotFound e) {
                throw new NodeNotFoundOnAlfrescoException(nodeId);
            }
            expiresAt = started + TimeUnit.SECONDS.toNanos(ttlSeconds);
        }

        private synchronized void renew() {
            if (closed || failure != null) return;
            try {
                assertValid();
                extend();
            } catch (RuntimeException e) {
                failure = e;
                renewal.cancel(false);
                log.error("Unable to renew Alfresco archive lock for node {}", nodeId, e);
            }
        }

        public synchronized void assertValid() {
            if (!managed) return;
            if (failure != null) throw new VaultException("Alfresco archive lock renewal failed for node " + nodeId, failure);
            if (closed || System.nanoTime() - expiresAt >= 0) {
                throw new VaultException("Alfresco archive lock expired for node " + nodeId);
            }
        }

        /** Refresh immediately before deleting; serialize deletion with background renewal. */
        public synchronized void delete(BooleanSupplier deletion) {
            assertValid();
            if (managed) {
                try {
                    extend();
                    assertValid();
                } catch (RuntimeException e) {
                    failure = e;
                    renewal.cancel(false);
                    throw e;
                }
            }
            if (deletion.getAsBoolean()) {
                closed = true;
                if (renewal != null) renewal.cancel(false);
            }
        }

        /** Our temporary lock must not be restored later as part of the archived metadata. */
        public void stripLockMetadata(Node node) {
            if (!managed) return;
            node.setIsLocked(false);
            if (node.getAspectNames() != null) node.getAspectNames().remove("cm:lockable");
            if (node.getProperties() instanceof Map<?, ?> properties) {
                for (val key : List.of("cm:lockOwner", "cm:lockType", "cm:lockLifetime", "cm:expiryDate")) {
                    properties.remove(key);
                }
            }
        }

        @Override
        public synchronized void close() {
            if (!managed || closed) return;
            closed = true;
            if (renewal != null) renewal.cancel(false);
            // Once ownership is uncertain, let the finite lease expire rather than unlock another operation.
            if (failure != null || System.nanoTime() - expiresAt >= 0) return;
            try {
                nodesApi.unlockNode(nodeId, null, null);
            } catch (FeignException.NotFound ignored) {
                // A delete may have succeeded even if its response was lost.
            } catch (RuntimeException e) {
                log.error("Unable to release Alfresco archive lock for node {}; it will expire", nodeId, e);
            }
        }
    }
}
