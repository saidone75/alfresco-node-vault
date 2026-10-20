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
import com.mongodb.client.MongoClient;
import jakarta.annotation.PreDestroy;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;
import net.javacrumbs.shedlock.support.KeepAliveLockProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** MongoDB-backed locks, acquired programmatically at service and job boundaries. */
@Configuration
@EnableConfigurationProperties(LockingProperties.class)
public class ShedLockConfig {

    // Keep this executor private: exposing it as a bean could make Spring use it
    // for application jobs, whose blocking work would then prevent lock renewal.
    private final ScheduledExecutorService renewalScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        val thread = new Thread(runnable, "shedlock-renewal");
        thread.setDaemon(true);
        return thread;
    });

    @Bean
    public LockProvider lockProvider(MongoClient mongoClient,
                                    @Value("${spring.data.mongodb.database}") String database) {
        return new KeepAliveLockProvider(new MongoLockProvider(mongoClient.getDatabase(database)), renewalScheduler);
    }

    @PreDestroy
    public void stopRenewalScheduler() {
        renewalScheduler.shutdown();
    }
}
