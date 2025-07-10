package org.saidone.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.component.BaseComponent;
import org.saidone.service.EthereumService;
import org.saidone.service.NodeService;
import org.saidone.service.content.ContentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically computes document hashes and stores them on the blockchain.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.notarization-job.enabled", havingValue = "true")
@EnableScheduling
@Slf4j
public class DocumentNotarizationJob extends BaseComponent {

    private final NodeService nodeService;
    private final ContentService contentService;
    private final EthereumService ethereumService;

    @Value("${application.service.vault.hash-algorithm}")
    private String algorithm;

    @Scheduled(cron = "${application.notarization-job.cron-expression}")
    void notarize() {
        doNotarize();
    }

    private synchronized void doNotarize() {
        for (val node : nodeService.findByTxId(null)) {
            val checksum = contentService.computeHash(node.getId(), algorithm);
            val txId = ethereumService.storeHash(node.getId(), checksum);
            node.setNotarizationTxId(txId);
            nodeService.save(node);
        }
    }
}
