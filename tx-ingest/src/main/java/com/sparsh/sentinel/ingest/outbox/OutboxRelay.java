package com.sparsh.sentinel.ingest.outbox;

import com.sparsh.sentinel.ingest.config.IngestProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes outbox rows to Kafka, oldest first, and marks each one published once the broker
 * has acknowledged it.
 *
 * <p>Delivery is <b>at least once</b>. If the process dies after the broker acknowledged a send
 * but before the mark is committed, that event goes out again on restart. Consumers therefore
 * have to be idempotent on {@code transactionId} - scoring-service is. Exactly-once would need
 * Kafka transactions spanning the database, which Kafka cannot do; at-least-once plus an
 * idempotent consumer is the standard answer.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final IngestProperties properties;
    private final Clock clock;

    public OutboxRelay(OutboxRepository outbox,
                       KafkaTemplate<String, String> kafka,
                       IngestProperties properties,
                       Clock clock) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Publishes one batch.
     *
     * <p>Stops at the first failed send rather than skipping past it. Publishing a later event
     * ahead of an earlier one for the same customer would break the ordering that the history
     * rules in scoring depend on. The rows sent before the failure are still marked and committed.
     *
     * @return how many events were published
     */
    @Scheduled(fixedDelayString = "${sentinel.ingest.outbox.poll-interval-ms}")
    @Transactional
    public int publishPending() {
        List<OutboxEvent> batch = outbox.claimUnpublished(properties.outbox().batchSize());
        int published = 0;

        for (OutboxEvent event : batch) {
            try {
                kafka.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException | TimeoutException e) {
                log.warn("Outbox event {} not published, will retry: {}", event.getId(), e.getMessage());
                break;
            }
            event.markPublished(clock.instant());
            published++;
        }

        if (published > 0) {
            log.debug("Published {} of {} claimed outbox events", published, batch.size());
        }
        return published;
    }
}
