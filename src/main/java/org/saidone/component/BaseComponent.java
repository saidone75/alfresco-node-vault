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

package org.saidone.component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.saidone.misc.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Base class for all Spring managed components of the Alfresco Node Vault.
 *
 * <p>This class centralises common lifecycle handling.  It logs a message when
 * a component is initialised or destroyed and exposes a helper method to
 * gracefully close the {@link ApplicationContext} and terminate the
 * application.</p>
 */
@Slf4j
public abstract class BaseComponent implements ApplicationContextAware {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Stores the injected {@link ApplicationContext} for later use.
     */
    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Called after dependency injection is complete.
     * Logs a startup message indicating that the component is ready.
     */
    @PostConstruct
    public void init() {
        log.info("{} Starting {}", Constants.START_PREFIX, this.getClass().getSimpleName());
    }

    /**
     * Called just before the bean is destroyed.
     * Logs a shutdown message indicating that the component is stopping.
     */
    @PreDestroy
    public void stop() {
        log.info("{} Stopping {}", Constants.STOP_PREFIX, this.getClass().getSimpleName());
    }

    /**
     * Closes the Spring context and terminates the JVM with the given exit code.
     *
     * @param exitCode exit code returned to the operating system
     */
    public void shutDown(int exitCode) {
        log.info("Shutting down application");
        ((ConfigurableApplicationContext) applicationContext).close();
        System.exit(exitCode);
    }

}