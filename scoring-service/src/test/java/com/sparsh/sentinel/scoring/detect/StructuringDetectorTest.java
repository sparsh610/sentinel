package com.sparsh.sentinel.scoring.detect;

import com.sparsh.sentinel.scoring.alert.AlertRepository;
import com.sparsh.sentinel.scoring.score.ScoredTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;

import static com.sparsh.sentinel.scoring.TestEvents.BOOKED;
import static com.sparsh.sentinel.scoring.TestEvents.PROPERTIES;
import static com.sparsh.sentinel.scoring.TestEvents.cashDeposit;
import static com.sparsh.sentinel.scoring.TestEvents.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StructuringDetectorTest {

    private static final BigDecimal FLOOR = new BigDecimal("9000");
    private static final BigDecimal CEILING = new BigDecimal("10000");

    @Mock
    private ScoredTransactionRepository ledger;

    @Mock
    private AlertRepository alerts;

    private StructuringDetector detector() {
        return new StructuringDetector(ledger, alerts, PROPERTIES);
    }

    @Test
    void depositsOutsideTheBandNeverQueryHistory() {
        assertThat(detector().evaluate(cashDeposit("8999.99"))).isEmpty();
        // At the threshold it is a large-cash case, not structuring.
        assertThat(detector().evaluate(cashDeposit("10000.00"))).isEmpty();
        assertThat(detector().evaluate(event("DEBIT", "CASH", "9500", null))).isEmpty();
        assertThat(detector().evaluate(event("CREDIT", "TRANSFER", "9500", "DE"))).isEmpty();

        verifyNoInteractions(ledger, alerts);
    }

    @Test
    void firesOnTheThirdDepositInTheBandWithinTheWindow() {
        when(ledger.countCashDepositsInBand("C-10001", FLOOR, CEILING,
                BOOKED.minus(Duration.ofHours(24)), BOOKED)).thenReturn(3L);
        when(alerts.existsFindingForCustomerSince(anyString(), any(), any())).thenReturn(false);

        assertThat(detector().evaluate(cashDeposit("9999.99")))
                .hasValueSatisfying(f -> {
                    assertThat(f.rule()).isEqualTo(Finding.Rule.STRUCTURING);
                    assertThat(f.reason()).startsWith("3 cash deposits").contains("24 hours");
                });
    }

    @Test
    void twoDepositsAreNotYetAPattern() {
        when(ledger.countCashDepositsInBand(anyString(), any(), any(), any(), any())).thenReturn(2L);

        assertThat(detector().evaluate(cashDeposit("9500"))).isEmpty();
    }

    @Test
    void firesOncePerPatternNotOncePerDeposit() {
        when(ledger.countCashDepositsInBand(anyString(), any(), any(), any(), any())).thenReturn(5L);
        when(alerts.existsFindingForCustomerSince("C-10001", Finding.Rule.STRUCTURING,
                BOOKED.minus(Duration.ofHours(24)))).thenReturn(true);

        assertThat(detector().evaluate(cashDeposit("9500"))).isEmpty();
    }
}
