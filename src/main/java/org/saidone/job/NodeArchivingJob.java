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

package org.saidone.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.saidone.component.BaseComponent;
import org.saidone.service.AlfrescoService;
import org.saidone.service.VaultService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job responsible for archiving Alfresco nodes based on a configured query.
 * <p>
 * This job is enabled conditionally via the property {@code application.archiving-job.enabled}.
 * When enabled, it runs according to the cron expression defined in
 * {@code application.archiving-job.cron-expression} and archives nodes matching the query
 * specified in {@code application.archiving-job.query}.
 * </p>
 * <p>
 * The job uses {@link AlfrescoService} to search for nodes and {@link VaultService} to archive them.
 * </p>
 * <p>
 * Thread-safe execution is ensured by synchronizing the archiving process.
 * </p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.archiving-job.enabled", havingValue = "true")
@EnableScheduling
@Slf4j
public class NodeArchivingJob extends BaseComponent {

    private final AlfrescoService alfrescoService;
    private final VaultService vaultService;

    @Value("${application.archiving-job.query}")
    private String query;

    /**
     * Scheduled method that triggers the archiving process according to the configured cron expression.
     */
    @Scheduled(cron = "${application.archiving-job.cron-expression}")
    void archiveNodes() {
        doArchiveNodes();
    }

    /**
     * Performs the actual archiving of nodes by searching with the configured query and processing each node.
     * This method is synchronized to prevent concurrent execution.
     */
    private synchronized void doArchiveNodes() {
        alfrescoService.searchAndProcess(query, vaultService::archiveNode);
    }

}