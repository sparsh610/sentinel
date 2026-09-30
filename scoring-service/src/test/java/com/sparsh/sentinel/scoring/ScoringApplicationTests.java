package com.sparsh.sentinel.scoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sparsh.sentinel.scoring.alert.AlertRepository;
import com.sparsh.sentinel.scoring.detect.Detector;
import com.sparsh.sentinel.scoring.event.TransactionListener;
import com.sparsh.sentinel.scoring.event.UnsupportedEventException;
import com.sparsh.sentinel.scoring.score.ScoredTransactionRepository;
import com.sparsh.sentinel.scoring.score.ScoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Context smoke test, plus the listener checks that depend on the application's real
 * ObjectMapper. Postgres and Kafka are stubbed; the round trip through both is a Testcontainers
 * test, scheduled for week 8.
 */
@SpringBootTest
class ScoringApplicationTests {

    @MockitoBean
    private ScoredTransactionRepository ledger;

    @MockitoBean
    private AlertRepository alertRepository;

    @MockitoBean
    private ScoringService scoringService;

    @Autowired
    private List<Detector> detectors;

    @Autowired
    private TransactionListener listener;

    @Test
    void everyDetectorIsWiredInEvenWithoutExportedModels() {
        // Three rules and two model detectors; the model ones stay quiet until models exist.
        assertThat(detectors).hasSize(5);
    }

    @Test
    void fieldsTheConsumerDoesNotKnowAreIgnored() throws Exception {
        listener.onTransaction("""
                {"schemaVersion":1,"transactionId":"6f1c1b7e-8a39-4c1e-9d55-0d2b1c6a7e10",
                 "customerId":"C-10001","direction":"CREDIT","channel":"CASH","amount":50,
                 "currency":"EUR","bookedAt":"2026-09-23T10:00:00Z","addedNextYear":"x"}
                """);

        verify(scoringService).score(any());
    }

    @Test
    void anUnknownSchemaVersionIsRejectedNotGuessedAt() {
        assertThatThrownBy(() -> listener.onTransaction("""
                {"schemaVersion":2,"transactionId":"6f1c1b7e-8a39-4c1e-9d55-0d2b1c6a7e10"}
                """)).isInstanceOf(UnsupportedEventException.class);

        verify(scoringService, never()).score(any());
    }

    @Test
    void malformedJsonFailsAsAParseError() {
        assertThatThrownBy(() -> listener.onTransaction("{not json"))
                .isInstanceOf(JsonProcessingException.class);
    }
}
