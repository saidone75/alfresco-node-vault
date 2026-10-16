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

package org.saidone.config;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.config.BlockingExecutionConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * WebFlux configuration running blocking controller methods off the event loop.
 * <p>
 * The REST controllers are written in blocking style (Feign, S3, MongoDB and Vault
 * calls). Controller methods that do not return a reactive type are invoked on a
 * dedicated thread pool instead of the Netty event loop threads, so a long archive,
 * restore or download does not stall the other connections, including the requests
 * proxied to Alfresco by the gateway.
 * </p>
 * <p>
 * Platform threads are used instead of virtual threads because, on Java 21, blocking
 * I/O inside {@code synchronized} methods (e.g. {@code AlfrescoService.restoreNode})
 * pins the carrier thread.
 * </p>
 */
@Configuration
public class WebFluxConfig implements WebFluxConfigurer, DisposableBean {

    private final ThreadPoolTaskExecutor blockingExecutor;

    public WebFluxConfig() {
        blockingExecutor = new ThreadPoolTaskExecutor();
        blockingExecutor.setCorePoolSize(16);
        blockingExecutor.setMaxPoolSize(64);
        blockingExecutor.setQueueCapacity(500);
        blockingExecutor.setThreadNamePrefix("vault-api-");
        blockingExecutor.initialize();
    }

    @Override
    public void configureBlockingExecution(BlockingExecutionConfigurer configurer) {
        configurer.setExecutor(blockingExecutor);
    }

    @Override
    public void destroy() {
        blockingExecutor.shutdown();
    }

}
