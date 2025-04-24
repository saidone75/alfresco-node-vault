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