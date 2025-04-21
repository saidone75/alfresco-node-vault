package org.saidone.component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.saidone.Constants;

@Slf4j
public abstract class BaseComponent {

    @PostConstruct
    public void init() {
        log.info("{} Starting {}", Constants.START_PREFIX, this.getClass().getSimpleName());
    }

    @PreDestroy
    public void stop() {
        log.info("{} Stopping {}", Constants.STOP_PREFIX, this.getClass().getSimpleName());
    }

}