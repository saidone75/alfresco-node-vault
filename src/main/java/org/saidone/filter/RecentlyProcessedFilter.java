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

package org.saidone.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.event.sdk.handling.filter.AbstractEventFilter;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * An event filter for Alfresco repository events that ensures each node is processed
 * only once within a configurable time threshold.
 * <p>
 * This filter prevents repeated processing of the same node event within a specified duration,
 * helping to avoid duplicate processing and reduce unnecessary load on the system.
 * A node ID is tracked in a thread-safe, static map upon successful test evaluation.
 * Further events for that node are rejected until the threshold expires.
 * <p>
 * The time threshold is provided when instantiating the filter via the {@link #of(long)} factory method.
 * The {@link #test(RepoEvent)} method checks if the node associated with the event has been processed
 * recently, returning {@code true} and updating the internal tracking map if the node is eligible,
 * or returning {@code false} otherwise.
 * <p>
 * The filter periodically cleans its tracking map to remove expired node entries using the
 * {@link #cleanRecentlyProcessedNodes()} scheduled method, minimizing memory usage.
 * <p>
 * Thread safety is ensured by the use of a {@link ConcurrentHashMap}.
 * Logging is provided for skipped and removed nodes for debugging and traceability.
 * <p>
 * This filter is typically used in event processing chains to enforce de-duplication logic on node events.
 *
 * @see org.alfresco.event.sdk.handling.filter.AbstractEventFilter
 */
@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class RecentlyProcessedFilter extends AbstractEventFilter {

    private long threshold;
    private static final ConcurrentHashMap<String, Long> recentlyProcessedNodes = new ConcurrentHashMap<>();

    private RecentlyProcessedFilter(long threshold) {
        this.threshold = threshold;
    }

    public static RecentlyProcessedFilter of(final long threshold) {
        return new RecentlyProcessedFilter(threshold);
    }

    @Override
    public boolean test(RepoEvent<DataAttributes<Resource>> repoEvent) {
        val nodeId = ((NodeResource) repoEvent.getData().getResource()).getId();
        if (!recentlyProcessedNodes.containsKey(nodeId) ||
                (recentlyProcessedNodes.get(nodeId) != null &&
                        (System.currentTimeMillis() >= recentlyProcessedNodes.get(nodeId)))) {
            /* process node */
            recentlyProcessedNodes.put(nodeId, System.currentTimeMillis() + threshold);
            return true;
        } else {
            /* skip */
            log.warn("Node {} was recently processed, skipping...", nodeId);
            return false;
        }
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void cleanRecentlyProcessedNodes() {
        recentlyProcessedNodes.entrySet().stream().filter(e -> e.getValue() < System.currentTimeMillis())
                .forEach(e -> {
                    log.debug("Removing node {} from recentlyProcessedNodes", e.getKey());
                    recentlyProcessedNodes.remove(e.getKey());
                });
    }

}
