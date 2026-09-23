package com.sparsh.sentinel.scoring.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sparsh.sentinel.scoring.event.UnsupportedEventException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(ScoringProperties.class)
public class ScoringConfig {

    /**
     * What happens to a record the listener throws on.
     *
     * <p>Two retries a second apart cover a database blip. After that the record goes to the
     * dead-letter topic and the partition moves on - one poisoned message must not stop every
     * customer behind it on the same partition from being scored.
     *
     * <p>A record that cannot be parsed, or has a schema version this service does not know,
     * will fail the same way however often it is retried, so it goes to the dead-letter topic at
     * once.
     *
     * <p>The destination is named explicitly rather than left to the recoverer's default. The
     * default was not the name that had been declared, so dead letters went to a topic nobody
     * monitored - and with auto-creation on, the broker created it silently.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> template, ScoringProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(properties.deadLetterTopic(), record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 2));
        handler.addNotRetryableExceptions(JsonProcessingException.class, UnsupportedEventException.class);
        return handler;
    }

    @Bean
    NewTopic deadLetterTopic(ScoringProperties properties) {
        return TopicBuilder.name(properties.deadLetterTopic())
                .partitions(properties.topicPartitions())
                .replicas(1)
                .build();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
