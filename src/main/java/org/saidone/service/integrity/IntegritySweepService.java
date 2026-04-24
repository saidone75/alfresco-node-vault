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
import org.saidone.model.IntegritySweepRun;
import org.saidone.model.NodeWrapper;
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
 * Executes notarization integrity checks for notarized nodes and stores run summaries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntegritySweepService extends BaseComponent {

    public static final String INTEGRITY_SWEEP_COLLECTION_NAME = "integrity_sweep_run";

    private final NodeService nodeService;
    private final NotarizationService notarizationService;
    private final MongoTemplate mongoTemplate;
    private final IntegritySweepMetrics metrics;

    @Value("${application.integrity-sweep.batch-size:200}")
    private int batchSize;

    public synchronized IntegritySweepRun runSweep(String trigger) {
        val run = new IntegritySweepRun();
        run.setStartedAt(Instant.now());
        run.setStatus("RUNNING");
        run.setTrigger(Strings.isBlank(trigger) ? "MANUAL" : trigger);
        mongoTemplate.save(run);

        try {
            Pageable pageable = PageRequest.of(0, Math.max(batchSize, 1), Sort.by(Sort.Direction.ASC, "_id"));
            Page<NodeWrapper> page;
            do {
                page = nodeService.findNotarized(pageable);
                for (val node : page.getContent()) {
                    run.setScanned(run.getScanned() + 1);
                    try {
                        notarizationService.checkNotarization(node.getId());
                        run.setPassed(run.getPassed() + 1);
                    } catch (NotarizationException e) {
                        run.setFailed(run.getFailed() + 1);
                        log.warn("Integrity check failed for node {}: {}", node.getId(), e.getMessage());
                    } catch (Exception e) {
                        run.setErrors(run.getErrors() + 1);
                        log.error("Unexpected error during integrity check for node {}", node.getId(), e);
                    }
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

    public Page<IntegritySweepRun> findRuns(Pageable pageable) {
        val sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Direction.DESC, "sat");

        val query = new Query().with(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort));
        val content = mongoTemplate.find(query, IntegritySweepRun.class);
        long total = mongoTemplate.count(new Query(), IntegritySweepRun.class);
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

}
