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

package org.saidone.service.integrity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.logging.log4j.util.Strings;
import org.saidone.component.BaseComponent;
import org.saidone.exception.NotarizationException;
import org.saidone.model.dto.CorruptedNodeDto;
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
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

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
    /** Enable random-sampled sweeps when notarized node count is high enough. */
    @Value("${application.integrity-sweep.random-batch-enabled:true}")
    private boolean randomBatchEnabled;
    /** Minimum notarized node count required before using random batch sampling. */
    @Value("${application.integrity-sweep.random-batch-threshold:10000}")
    private long randomBatchThreshold;
    /** Enables hybrid mode: deterministic coverage batch + optional random sample batch. */
    @Value("${application.integrity-sweep.hybrid-enabled:true}")
    private boolean hybridEnabled;
    /** Number of random nodes to add in hybrid mode (0 disables random addon). */
    @Value("${application.integrity-sweep.random-addon-size:50}")
    private int randomAddonSize;

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
            if (useReducedMode()) {
                long notarizedCount = nodeService.countNotarized();
                val coverageNodes = getCoverageBatch(notarizedCount);
                log.info("Running integrity sweep coverage batch: {} nodes (total notarized: {})",
                        coverageNodes.size(), notarizedCount);
                processNodes(run, coverageNodes);

                if (hybridEnabled && randomBatchEnabled && randomAddonSize > 0) {
                    val sampledNodes = nodeService.findNotarizedRandom(randomAddonSize)
                            .stream()
                            .filter(node -> !containsNodeId(coverageNodes, node.getId()))
                            .collect(Collectors.toList());
                    log.info("Running integrity sweep random addon batch: {} nodes", sampledNodes.size());
                    processNodes(run, sampledNodes);
                }
            } else {
                Pageable pageable = PageRequest.of(0, Math.max(batchSize, 1), Sort.by(Sort.Direction.ASC, "_id"));
                Page<NodeWrapperDto> page;
                do {
                    page = nodeService.findNotarized(pageable);
                    processNodes(run, page.getContent());
                    pageable = page.nextPageable();
                } while (page.hasNext());
            }
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

    private boolean useReducedMode() {
        long notarizedCount = nodeService.countNotarized();
        return notarizedCount > Math.max(randomBatchThreshold, 0);
    }

    private List<NodeWrapperDto> getCoverageBatch(long notarizedCount) {
        int effectiveBatchSize = Math.max(batchSize, 1);
        long totalPages = Math.max((long) Math.ceil((double) notarizedCount / effectiveBatchSize), 1L);
        long epochDay = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        int pageIndex = (int) Math.floorMod(epochDay, totalPages);
        Pageable pageable = PageRequest.of(pageIndex, effectiveBatchSize, Sort.by(Sort.Direction.ASC, "_id"));
        return nodeService.findNotarized(pageable).getContent();
    }

    private boolean containsNodeId(List<NodeWrapperDto> nodes, String nodeId) {
        return nodes.stream().anyMatch(node -> node.getId().equals(nodeId));
    }

    private void processNodes(IntegritySweepRunDto run, List<NodeWrapperDto> nodes) {
        for (val node : nodes) {
            run.setScanned(run.getScanned() + 1);
            checkNodeIntegrity(run, node);
        }
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
            clearCorruptedNode(node.getId());
        } catch (NotarizationException e) {
            run.setFailed(run.getFailed() + 1);
            run.getFailedNodeIds().add(node.getId());
            markCorruptedNode(node.getId(), e.getMessage(), run.getId());
            log.warn("Integrity check failed for node {}: {}", node.getId(), e.getMessage());
        } catch (Exception e) {
            run.setErrors(run.getErrors() + 1);
            run.getFailedNodeIds().add(node.getId());
            markCorruptedNode(node.getId(), e.getClass().getSimpleName(), run.getId());
            log.error("Unexpected error during integrity check for node {}", node.getId(), e);
        }
    }

    private void markCorruptedNode(String nodeId, String reason, String runId) {
        val now = Instant.now();
        val query = Query.query(org.springframework.data.mongodb.core.query.Criteria.where("_id").is(nodeId));
        val update = new Update()
                .set("lfs", now)
                .set("lat", now)
                .set("frs", Strings.isBlank(reason) ? "UNKNOWN" : reason)
                .set("rid", runId)
                .inc("att", 1)
                .setOnInsert("ffs", now);
        mongoTemplate.upsert(query, update, CorruptedNodeDto.class);
    }

    private void clearCorruptedNode(String nodeId) {
        mongoTemplate.remove(Query.query(org.springframework.data.mongodb.core.query.Criteria.where("_id").is(nodeId)), CorruptedNodeDto.class);
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

    public Page<CorruptedNodeDto> findCorruptedNodes(Pageable pageable) {
        val sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Direction.DESC, "lfs");
        val query = new Query().with(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort));
        val content = mongoTemplate.find(query, CorruptedNodeDto.class);
        long total = mongoTemplate.count(new Query(), CorruptedNodeDto.class);
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

}
