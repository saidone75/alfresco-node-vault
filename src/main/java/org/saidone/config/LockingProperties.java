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

package org.saidone.config;

import lombok.val;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Configuration shared by all instances coordinating operations in the same vault. */
@ConfigurationProperties("application.locking")
public record LockingProperties(
        @DefaultValue("2m") Duration nodeLockAtMostFor,
        @DefaultValue("1h") Duration jobLockAtMostFor,
        @DefaultValue("1m") Duration jobLockAtLeastFor) {

    public LockingProperties {
        // KeepAliveLockProvider requires a lease of at least 30 seconds.
        val minimumLease = Duration.ofSeconds(30);
        if (nodeLockAtMostFor == null || nodeLockAtMostFor.compareTo(minimumLease) < 0
                || jobLockAtMostFor == null || jobLockAtMostFor.compareTo(minimumLease) < 0) {
            throw new IllegalArgumentException("Node and job lock durations must be at least 30 seconds");
        }
        if (jobLockAtLeastFor == null || jobLockAtLeastFor.isNegative()
                || jobLockAtLeastFor.compareTo(jobLockAtMostFor) > 0) {
            throw new IllegalArgumentException("Minimum job lock duration must be between zero and the job lease duration");
        }
    }
}
