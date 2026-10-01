package com.sparsh.sentinel.scoring.alert;

import com.sparsh.sentinel.scoring.detect.Finding;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.sparsh.sentinel.scoring.TestEvents.cashDeposit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertLifecycleTest {

    private static Alert newAlert() {
        return Alert.raise(cashDeposit("15000"),
                List.of(new Finding(Finding.Rule.LARGE_CASH, new BigDecimal("0.60"), "Cash deposit")),
                Instant.parse("2026-10-01T09:00:00Z"));
    }

    @Test
    void anInvestigationTakesAnAlertFromOpenToInReviewToADecision() {
        Alert alert = newAlert();

        alert.moveTo(Alert.Status.IN_REVIEW);
        alert.moveTo(Alert.Status.ESCALATED);

        assertThat(alert.getStatus()).isEqualTo(Alert.Status.ESCALATED);
    }

    @Test
    void anAlertCannotBeDecidedWithoutBeingReviewed() {
        Alert alert = newAlert();

        assertThatThrownBy(() -> alert.moveTo(Alert.Status.CLOSED)).isInstanceOf(IllegalStateException.class);
        assertThat(alert.getStatus()).isEqualTo(Alert.Status.OPEN);
    }

    @Test
    void aDecidedAlertStaysDecided() {
        Alert alert = newAlert();
        alert.moveTo(Alert.Status.IN_REVIEW);
        alert.moveTo(Alert.Status.CLOSED);

        assertThatThrownBy(() -> alert.moveTo(Alert.Status.OPEN)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> alert.moveTo(Alert.Status.ESCALATED)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void repeatingAMoveIsHarmless() {
        Alert alert = newAlert();
        alert.moveTo(Alert.Status.IN_REVIEW);

        alert.moveTo(Alert.Status.IN_REVIEW);

        assertThat(alert.getStatus()).isEqualTo(Alert.Status.IN_REVIEW);
    }
}
