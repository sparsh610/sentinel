package com.sparsh.sentinel.ingest.outbox;

import com.sparsh.sentinel.ingest.config.IngestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The relay's one job is to never mark an event published that the broker did not take, and to
 * never let a later event overtake an earlier one.
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");

    @Mock
    private OutboxRepository outbox;

    @Mock
    private KafkaTemplate<String, String> kafka;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        IngestProperties properties = new IngestProperties(
                "transactions", 3, new IngestProperties.Outbox(true, 500, 100));
        relay = new OutboxRelay(outbox, kafka, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void marksEachEventPublishedOnceTheBrokerAcknowledgesIt() {
        OutboxEvent first = event("C-1", "{\"n\":1}");
        OutboxEvent second = event("C-2", "{\"n\":2}");
        when(outbox.claimUnpublished(100)).thenReturn(List.of(first, second));
        when(kafka.send("transactions", "C-1", "{\"n\":1}")).thenReturn(acknowledged());
        when(kafka.send("transactions", "C-2", "{\"n\":2}")).thenReturn(acknowledged());

        assertThat(relay.publishPending()).isEqualTo(2);

        assertThat(first.getPublishedAt()).isEqualTo(NOW);
        assertThat(second.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void stopsAtTheFirstFailureSoNothingOvertakesIt() {
        OutboxEvent first = event("C-1", "one");
        OutboxEvent failing = event("C-1", "two");
        OutboxEvent third = event("C-1", "three");
        when(outbox.claimUnpublished(100)).thenReturn(List.of(first, failing, third));
        when(kafka.send("transactions", "C-1", "one")).thenReturn(acknowledged());
        when(kafka.send("transactions", "C-1", "two"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThat(relay.publishPending()).isEqualTo(1);

        assertThat(first.getPublishedAt()).isEqualTo(NOW);
        assertThat(failing.getPublishedAt()).isNull();
        assertThat(third.getPublishedAt()).isNull();
        verify(kafka, never()).send("transactions", "C-1", "three");
    }

    private static OutboxEvent event(String key, String payload) {
        return new OutboxEvent(UUID.randomUUID(), "transactions", key, payload, NOW.minusSeconds(1));
    }

    private static CompletableFuture<SendResult<String, String>> acknowledged() {
        return CompletableFuture.completedFuture(null);
    }
}
