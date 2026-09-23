package com.sparsh.sentinel.ingest.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(IngestProperties.class)
public class IngestConfig {

    /**
     * Declared rather than left to broker auto-creation, which would create it with one
     * partition and quietly cap scoring at a single consumer.
     */
    @Bean
    NewTopic transactionsTopic(IngestProperties properties) {
        return TopicBuilder.name(properties.topic())
                .partitions(properties.topicPartitions())
                .replicas(1)
                .build();
    }

    /** Injected rather than calling Instant.now() inline, so tests can pin time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Scheduling exists only to drive the outbox relay, so it is switched on with it. */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "sentinel.ingest.outbox", name = "relay-enabled", havingValue = "true")
    static class RelaySchedulingConfig {
    }
}
