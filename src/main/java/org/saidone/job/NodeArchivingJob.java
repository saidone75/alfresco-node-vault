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
 * Schedules and executes the periodic archiving of Alfresco nodes matching a configurable query.
 * <p>
 * This component runs only if the property 'application.archiving-job.enabled' is set to true.
 * It uses the AlfrescoService to search for nodes based on a query string and archives each found node
 * through the VaultService. The schedule for execution and the search query are defined in the application configuration.
 * Archiving execution is synchronized to avoid overlapping runs.
 * <p>
 * The job is intended to automate the archival of nodes in regular intervals and ensure that
 * only one instance of the archive operation runs at any given time.
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