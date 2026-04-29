/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
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

package org.saidone.service.integrity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.logging.log4j.util.Strings;
import org.saidone.component.BaseComponent;
import org.saidone.exception.NotarizationException;
import org.saidone.model.dto.IntegritySweepRunDto;
import org.saidone.model.dto.NodeWrapperDto;
import org.saidone.monitor.IntegritySweepMetrics;
import org.saidone.service.NodeService;
import org.saidone.service.notarization.NotarizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Service responsible for running end-to-end integrity sweeps across notarized
 * vault nodes.
 *
 * <p>Each sweep iterates through notarized nodes in batches, invokes
 * {@link NotarizationService#checkNotarization(String)} for every node, and
 * persists a summarized {@link IntegritySweepRunDto} document that captures
 * run status, duration, and per-node outcomes.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntegritySweepService extends BaseComponent {

    /** Service used to page and retrieve notarized nodes from the vault. */
    private final NodeService nodeService;
    /** Service used to validate notarization data for each scanned node. */
    private final NotarizationService notarizationService;
    /** MongoDB access used to persist and query integrity sweep run metadata. */
    private final MongoTemplate mongoTemplate;
    /** Metrics collector for reporting completed sweep run statistics. */
    private final IntegritySweepMetrics metrics;

    /** Maximum number of notarized nodes to process per page during a sweep. */
    @Value("${application.integrity-sweep.batch-size:200}")
    private int batchSize;

    /**
     * Executes a full integrity sweep for all notarized nodes.
     *
     * <p>The run is created with status {@code RUNNING}, updated as nodes are
     * scanned, then finalized as {@code COMPLETED} or {@code FAILED}. The final
     * run document is persisted and published to the metrics collector even when
     * errors occur.</p>
     *
     * @param trigger the run trigger source (for example {@code MANUAL} or
     *                {@code SCHEDULED}); blank values default to {@code MANUAL}
     * @return persisted sweep run summary with timing and counters
     */
    public synchronized IntegritySweepRunDto runSweep(String trigger) {
        val run = new IntegritySweepRunDto();
        run.setStartedAt(Instant.now());
        run.setStatus("RUNNING");
        run.setTrigger(Strings.isBlank(trigger) ? "MANUAL" : trigger);
        mongoTemplate.save(run);

        try {
            Pageable pageable = PageRequest.of(0, Math.max(batchSize, 1), Sort.by(Sort.Direction.ASC, "_id"));
            Page<NodeWrapperDto> page;
            do {
                page = nodeService.findNotarized(pageable);
                for (val node : page.getContent()) {
                    run.setScanned(run.getScanned() + 1);
                    checkNodeIntegrity(run, node);
                }
                pageable = page.nextPageable();
            } while (page.hasNext());
            run.setStatus("COMPLETED");
        } catch (Exception e) {
            run.setStatus("FAILED");
            log.error("Integrity sweep run failed", e);
        } finally {
            run.setEndedAt(Instant.now());
            run.setDurationMs(Duration.between(run.getStartedAt(), run.getEndedAt()).toMillis());
            mongoTemplate.save(run);
            metrics.recordRun(run);
        }
        return run;
    }

    /**
     * Executes notarization verification for a single node and updates run counters.
     *
     * @param run  the currently active sweep run accumulator
     * @param node the node to verify
     */
    private void checkNodeIntegrity(IntegritySweepRunDto run, NodeWrapperDto node) {
        try {
            notarizationService.checkNotarization(node.getId());
            run.setPassed(run.getPassed() + 1);
        } catch (NotarizationException e) {
            run.setFailed(run.getFailed() + 1);
            run.getFailedNodeIds().add(node.getId());
            log.warn("Integrity check failed for node {}: {}", node.getId(), e.getMessage());
        } catch (Exception e) {
            run.setErrors(run.getErrors() + 1);
            run.getFailedNodeIds().add(node.getId());
            log.error("Unexpected error during integrity check for node {}", node.getId(), e);
        }
    }

    /**
     * Retrieves persisted integrity sweep runs using pagination.
     *
     * <p>If the caller does not provide explicit sort criteria, runs are
     * returned in descending order by the {@code sat} field.</p>
     *
     * @param pageable paging and optional sorting information
     * @return page of recorded {@link IntegritySweepRunDto} runs
     */
    public Page<IntegritySweepRunDto> findRuns(Pageable pageable) {
        val sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Direction.DESC, "sat");

        val query = new Query().with(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort));
        val content = mongoTemplate.find(query, IntegritySweepRunDto.class);
        long total = mongoTemplate.count(new Query(), IntegritySweepRunDto.class);
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

}
