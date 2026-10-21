/*
 * Alfresco Node Vault - ad aeternam documentorum conservationem
 * Copyright (C) 2025-2026 Saidone
 * SPDX-License-Identifier: GPL-3.0-or-later
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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.val;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Micrometer metrics for public vault mutation requests and newly archived content. */
@Component
public class VaultOperationMetrics {

    public enum Operation {
        ARCHIVE, RESTORE, DELETE
    }

    public enum Outcome {
        SUCCESS, ERROR, SKIPPED
    }

    private final Map<Operation, Map<Outcome, Counter>> operationCounters = new EnumMap<>(Operation.class);
    private final Map<Operation, Map<Outcome, Timer>> operationTimers = new EnumMap<>(Operation.class);
    private final Counter archivedBytesTotal;
    private final boolean enabled;

    public VaultOperationMetrics(MeterRegistry meterRegistry,
                                 @Value("${application.metrics.vault-operations.enabled:true}") boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            this.archivedBytesTotal = null;
            return;
        }
        for (val operation : Operation.values()) {
            val counters = new EnumMap<Outcome, Counter>(Outcome.class);
            val timers = new EnumMap<Outcome, Timer>(Outcome.class);
            for (val outcome : Outcome.values()) {
                val operationTag = operation.name().toLowerCase(Locale.ROOT);
                val outcomeTag = outcome.name().toLowerCase(Locale.ROOT);
                counters.put(outcome, Counter.builder("vault_operations_total")
                        .description("Number of public vault mutation requests by operation and outcome")
                        .tags("operation", operationTag, "outcome", outcomeTag)
                        .register(meterRegistry));
                timers.put(outcome, Timer.builder("vault_operation_duration")
                        .description("Duration of public vault mutation requests, including lock acquisition and rollback")
                        .tags("operation", operationTag, "outcome", outcomeTag)
                        .publishPercentileHistogram()
                        .maximumExpectedValue(Duration.ofMinutes(10))
                        .register(meterRegistry));
            }
            operationCounters.put(operation, counters);
            operationTimers.put(operation, timers);
        }
        this.archivedBytesTotal = Counter.builder("vault_archived_bytes_total")
                .description("Original content bytes newly archived successfully, excluding retries and rollbacks")
                .register(meterRegistry);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void recordOperation(Operation operation, Outcome outcome, long durationNanos) {
        if (!enabled) return;
        operationCounters.get(operation).get(outcome).increment();
        operationTimers.get(operation).get(outcome).record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordArchivedBytes(long bytes) {
        if (enabled && bytes > 0) archivedBytesTotal.increment(bytes);
    }

}
