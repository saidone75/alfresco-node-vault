package org.saidone.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.saidone.component.BaseComponent;
import org.saidone.service.NodeService;
import org.saidone.service.notarization.EthereumService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules the periodic notarization of all nodes missing a transaction id.
 * <p>
 * At each execution the job computes a checksum of the node content using the
 * configured algorithm and stores the result on the blockchain via the
 * {@link EthereumService}. The returned transaction id is then saved back on
 * the node. Execution is synchronized and enabled only when the property
 * {@code application.notarization-job.enabled} is set to {@code true}.
 * </p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.notarization-job.enabled", havingValue = "true")
@EnableScheduling
@Slf4j
public class NodeNotarizationJob extends BaseComponent {

    private final NodeService nodeService;
    private final EthereumService ethereumService;

    /**
     * Hash algorithm used to generate the checksum that will be notarised.
     * The value is injected from the
     * {@code application.service.vault.hash-algorithm} property.
     */
    @Value("${application.service.vault.hash-algorithm}")
    private String algorithm;

    /**
     * Scheduled entry point triggered according to the configured cron expression.
     */
    @Scheduled(cron = "${application.notarization-job.cron-expression}")
    void notarize() {
        doNotarize();
    }

    /**
     * Performs the notarization of all nodes currently lacking a transaction id.
     * This method is synchronized to avoid concurrent executions.
     */
    private synchronized void doNotarize() {
        for (val node : nodeService.findByTxId(null)) {
            try {
                ethereumService.notarizeNode(node.getId());
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
            }
        }
    }

}
