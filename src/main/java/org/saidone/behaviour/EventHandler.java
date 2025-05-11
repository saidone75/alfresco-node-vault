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

package org.saidone.behaviour;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.event.sdk.handling.filter.AspectAddedFilter;
import org.alfresco.event.sdk.handling.filter.EventFilter;
import org.alfresco.event.sdk.handling.filter.NodeTypeFilter;
import org.alfresco.event.sdk.handling.handler.OnNodeCreatedEventHandler;
import org.alfresco.event.sdk.handling.handler.OnNodeUpdatedEventHandler;
import org.alfresco.repo.event.v1.model.*;
import org.saidone.filter.RecentlyProcessedFilter;
import org.saidone.model.alfresco.AlfrescoContentModel;
import org.saidone.model.alfresco.AnvContentModel;
import org.saidone.component.BaseComponent;
import org.saidone.service.VaultService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Handles Alfresco repository events for archiving nodes when certain criteria are met.
 * <p>
 * This component listens to node-related events via the Alfresco Event SDK and triggers node archiving
 * using the configured {@link VaultService}. Event handling is conditional, allowing activation through
 * application properties.
 * <p>
 * Events are filtered based on:
 * - Node type (must match {@code AlfrescoContentModel.TYPE_CONTENT}),
 * - Presence of a specific archive aspect,
 * - Whether the node has been processed recently (using a configurable threshold to avoid duplicate processing).
 * <p>
 * When an event passes the filters, the handler will archive the targeted node and log the operation outcome.
 * Any errors during archiving are also logged.
 * <p>
 * Activation of this handler depends on the {@code application.event-handler.enabled} property.
 *
 * @see VaultService#archiveNode(String)
 * @see org.alfresco.event.sdk.handling.filter.EventFilter
 * @see org.alfresco.event.sdk.handling.handler.OnNodeCreatedEventHandler
 * @see org.alfresco.event.sdk.handling.handler.OnNodeUpdatedEventHandler
 */
@Component
@ConditionalOnProperty(name = "application.event-handler.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class EventHandler extends BaseComponent implements OnNodeCreatedEventHandler, OnNodeUpdatedEventHandler {

    private final VaultService vaultService;

    @Value("${application.same-node-processing-threshold}")
    private static long threshold;

    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        val nodeResource = (NodeResource) event.getData().getResource();
        log.info("Archive request received for node: {}", nodeResource.getId());
        try {
            vaultService.archiveNode(nodeResource.getId());
            log.info("Node {} successfully archived", nodeResource.getId());
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    public EventFilter getEventFilter() {
        return (NodeTypeFilter.of(AlfrescoContentModel.TYPE_CONTENT))
                .and(AspectAddedFilter.of(AnvContentModel.ASP_ARCHIVE))
                .and(RecentlyProcessedFilter.of(threshold));
    }

    @Override
    public Set<EventType> getHandledEventTypes() {
        return Set.of(EventType.NODE_UPDATED);
    }

}