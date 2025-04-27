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

    @Scheduled(cron = "${application.archiving-job.cron-expression}")
    void archiveNodes() {
        doArchiveNodes();
    }

    private synchronized void doArchiveNodes() {
        alfrescoService.searchAndProcess(query, vaultService::archiveNode);
    }

}