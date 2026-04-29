/*
 * Alfresco Node Vault - ad aeternam documentorum conservationem
 * Copyright (C) 2025-2026 Saidone
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.component.BaseComponent;
import org.saidone.service.NodeService;
import org.saidone.service.notarization.NotarizationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that notarizes vault nodes that still miss a blockchain transaction id.
 * <p>
 * At each execution the job retrieves nodes where {@code ntx} is {@code null}
 * and delegates the notarization flow to {@link NotarizationService}. Successful
 * notarization stores the returned transaction id on the related node. Execution
 * is synchronized to avoid overlapping runs and enabled only when the property
 * {@code application.notarization-job.enabled} is set to {@code true}.
 * </p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.notarization-job.enabled", havingValue = "true")
@EnableScheduling
@Slf4j
public class NodeNotarizationJob extends BaseComponent {

    /** Service used to query nodes that require notarization. */
    private final NodeService nodeService;

    /** Service responsible for notarization and transaction id persistence. */
    private final NotarizationService notarizationService;

    /**
     * Scheduled entry point triggered according to
     * {@code application.notarization-job.cron-expression}.
     * Delegates execution to {@link #doNotarize()}.
     */
    @Scheduled(cron = "${application.notarization-job.cron-expression}")
    void notarize() {
        doNotarize();
    }

    /**
     * Iterates over all nodes with a missing transaction id and requests
     * notarization for each of them. Exceptions for individual nodes are
     * logged and do not stop processing of the remaining nodes.
     * This method is synchronized to prevent concurrent executions.
     */
    private synchronized void doNotarize() {
        for (val node : nodeService.findByNtx(null)) {
            try {
                notarizationService.notarizeNode(node.getId());
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
            }
        }
    }

}
