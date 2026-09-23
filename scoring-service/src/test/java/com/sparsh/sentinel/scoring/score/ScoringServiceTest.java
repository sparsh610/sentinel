package com.sparsh.sentinel.scoring.score;

import com.sparsh.sentinel.scoring.alert.Alert;
import com.sparsh.sentinel.scoring.alert.AlertFinding;
import com.sparsh.sentinel.scoring.alert.AlertRepository;
import com.sparsh.sentinel.scoring.detect.Detector;
import com.sparsh.sentinel.scoring.detect.Finding;
import com.sparsh.sentinel.scoring.event.TransactionEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.sparsh.sentinel.scoring.TestEvents.cashDeposit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoringServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-23T10:00:05Z");

    @Mock
    private ScoredTransactionRepository ledger;

    @Mock
    private AlertRepository alerts;

    @Mock
    private Detector first;

    @Mock
    private Detector second;

    private ScoringService service() {
        return new ScoringService(ledger, alerts, List.of(first, second), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aRedeliveredTransactionIsNotScoredTwice() {
        when(ledger.recordIfAbsent(any())).thenReturn(0);

        assertThat(service().score(cashDeposit("15000"))).isEmpty();

        verifyNoInteractions(first, second);
        verify(alerts, never()).save(any());
    }

    @Test
    void oneAlertCarriesEveryFindingAndTheHighestScore() {
        TransactionEvent tx = cashDeposit("15000");
        when(ledger.recordIfAbsent(any())).thenReturn(1);
        when(first.evaluate(tx)).thenReturn(Optional.of(
                new Finding(Finding.Rule.LARGE_CASH, new BigDecimal("0.60"), "large")));
        when(second.evaluate(tx)).thenReturn(Optional.of(
                new Finding(Finding.Rule.HIGH_RISK_JURISDICTION, new BigDecimal("0.75"), "jurisdiction")));
        when(alerts.save(any())).thenAnswer(call -> call.getArgument(0));

        Alert alert = service().score(tx).orElseThrow();

        assertThat(alert.getScore()).isEqualByComparingTo("0.75");
        assertThat(alert.getStatus()).isEqualTo(Alert.Status.OPEN);
        assertThat(alert.getRaisedAt()).isEqualTo(NOW);
        assertThat(alert.getTransactionId()).isEqualTo(tx.transactionId());
        assertThat(alert.getFindings()).extracting(AlertFinding::getRule)
                .containsExactlyInAnyOrder(Finding.Rule.LARGE_CASH, Finding.Rule.HIGH_RISK_JURISDICTION);
    }

    @Test
    void aCleanTransactionRaisesNothing() {
        TransactionEvent tx = cashDeposit("40");
        when(ledger.recordIfAbsent(any())).thenReturn(1);
        when(first.evaluate(tx)).thenReturn(Optional.empty());
        when(second.evaluate(tx)).thenReturn(Optional.empty());

        assertThat(service().score(tx)).isEmpty();
        verify(alerts, never()).save(any());
    }
}
