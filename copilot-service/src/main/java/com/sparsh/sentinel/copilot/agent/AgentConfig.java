package com.sparsh.sentinel.copilot.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    private final InvestigationRepository investigations;

    public AgentConfig(InvestigationRepository investigations) {
        this.investigations = investigations;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Marks runs that were cut off by a restart as failed, so none says RUNNING forever. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failInterruptedRuns() {
        int interrupted = investigations.failInterrupted(clock().instant());
        if (interrupted > 0) {
            log.warn("Marked {} investigation(s) interrupted by the last restart as FAILED", interrupted);
        }
    }
}
