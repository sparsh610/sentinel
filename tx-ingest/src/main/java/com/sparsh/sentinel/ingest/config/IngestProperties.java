package com.sparsh.sentinel.ingest.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param topic           the topic every accepted transaction is published to.
 * @param topicPartitions partitions created for it. This is the ceiling on how many scoring
 *                        consumers can work in parallel.
 * @param outbox          how the outbox relay behaves.
 */
@Validated
@ConfigurationProperties(prefix = "sentinel.ingest")
public record IngestProperties(

        @NotBlank
        String topic,

        @Min(1) @Max(64)
        int topicPartitions,

        @Valid
        Outbox outbox
) {

    /**
     * @param relayEnabled   off in tests, and on any second instance that should only accept
     *                       traffic. Row locking makes two relays safe, but not useful.
     * @param pollIntervalMs how long an accepted transaction can wait before it is published.
     * @param batchSize      rows claimed per poll.
     */
    public record Outbox(

            boolean relayEnabled,

            @Min(50)
            long pollIntervalMs,

            @Min(1) @Max(1000)
            int batchSize
    ) {
    }
}
