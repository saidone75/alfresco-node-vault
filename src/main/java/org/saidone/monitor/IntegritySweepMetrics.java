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

package org.saidone.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.saidone.model.dto.IntegritySweepRunDto;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer metrics for integrity sweep executions.
 */
@Component
public class IntegritySweepMetrics {

    private final AtomicLong lastRunTimestamp = new AtomicLong(0L);
    private final AtomicLong lastRunDuration = new AtomicLong(0L);

    private final Counter sweepRunsTotal;
    private final Counter sweepFailuresTotal;
    private final Counter sweepErrorsTotal;

    public IntegritySweepMetrics(MeterRegistry meterRegistry) {
        this.sweepRunsTotal = Counter.builder("integrity_sweep_runs_total")
                .description("Number of executed integrity sweep runs")
                .register(meterRegistry);

        this.sweepFailuresTotal = Counter.builder("integrity_sweep_failures_total")
                .description("Total number of integrity mismatches found")
                .register(meterRegistry);

        this.sweepErrorsTotal = Counter.builder("integrity_sweep_errors_total")
                .description("Total number of technical errors during integrity sweep")
                .register(meterRegistry);

        Gauge.builder("integrity_sweep_last_run_timestamp", lastRunTimestamp, AtomicLong::doubleValue)
                .description("Epoch milliseconds of the latest completed integrity sweep")
                .register(meterRegistry);

        Gauge.builder("integrity_sweep_last_duration_ms", lastRunDuration, AtomicLong::doubleValue)
                .description("Duration in milliseconds of the latest completed integrity sweep")
                .register(meterRegistry);
    }

    public void recordRun(IntegritySweepRunDto run) {
        sweepRunsTotal.increment();
        sweepFailuresTotal.increment(run.getFailed());
        sweepErrorsTotal.increment(run.getErrors());
        if (run.getEndedAt() != null) {
            lastRunTimestamp.set(run.getEndedAt().toEpochMilli());
        }
        lastRunDuration.set(run.getDurationMs());
    }

}
