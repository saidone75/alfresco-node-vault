/*
 *  Alfresco Node Vault - archive today, accelerate tomorrow
 *  Copyright (C) 2025 Saidone
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
 * Abstract base class for components within the Alfresco Node Vault application.
 * <p>
 * Provides lifecycle logging for startup and shutdown events, and a method to
 * programmatically shut down the Spring application context and exit the JVM.
 * </p>
 */
@Slf4j
public abstract class BaseComponent implements ApplicationContextAware {

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Initialization callback invoked after the component's dependencies have been injected.
     * Logs a startup message indicating the component is starting.
     */
    @PostConstruct
    public void init() {
        log.info("{} Starting {}", Constants.START_PREFIX, this.getClass().getSimpleName());
    }

    /**
     * Destruction callback invoked before the component is removed from the context.
     * Logs a shutdown message indicating the component is stopping.
     */
    @PreDestroy
    public void stop() {
        log.info("{} Stopping {}", Constants.STOP_PREFIX, this.getClass().getSimpleName());
    }

    /**
     * Shuts down the Spring application and exits the JVM with the given exit code.
     *
     * @param exitCode the exit code to return to the operating system
     */
    public void shutDown(int exitCode) {
        log.info("Shutting down application");
        ((ConfigurableApplicationContext) applicationContext).close();
        System.exit(exitCode);
    }

}