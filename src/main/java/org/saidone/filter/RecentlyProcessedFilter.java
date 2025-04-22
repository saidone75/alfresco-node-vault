package org.saidone.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.event.sdk.handling.filter.AbstractEventFilter;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

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
        var nodeId = ((NodeResource) repoEvent.getData().getResource()).getId();
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

    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void cleanRecentlyProcessedNodes() {
        recentlyProcessedNodes.entrySet().stream().filter(e -> (e.getValue() + threshold) < System.currentTimeMillis())
                .forEach(e -> {
                    log.debug("Removing node {} from recentlyProcessedNodes", e.getKey());
                    recentlyProcessedNodes.remove(e.getKey());
                });
    }

}
