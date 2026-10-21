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

package org.saidone.job;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Selects exactly one schedule for the shared, lock-protected archiving job. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "application.archiving-job.enabled", havingValue = "true")
class NodeArchivingScheduling {

    @Bean
    @ConditionalOnProperty(name = "application.archiving-job.scheduling-mode", havingValue = "cron", matchIfMissing = true)
    CronSchedule archivingCronSchedule(NodeArchivingJob job) {
        return new CronSchedule(job);
    }

    @Bean
    @ConditionalOnProperty(name = "application.archiving-job.scheduling-mode", havingValue = "fixed-delay")
    FixedDelaySchedule archivingFixedDelaySchedule(NodeArchivingJob job) {
        return new FixedDelaySchedule(job);
    }

    @RequiredArgsConstructor
    static class CronSchedule {
        private final NodeArchivingJob job;

        @Scheduled(cron = "${application.archiving-job.cron-expression}")
        void run() {
            job.archiveNodes();
        }
    }

    @RequiredArgsConstructor
    static class FixedDelaySchedule {
        private final NodeArchivingJob job;

        @Scheduled(fixedDelayString = "${application.archiving-job.fixed-delay:10s}")
        void run() {
            job.archiveNodes();
        }
    }
}
