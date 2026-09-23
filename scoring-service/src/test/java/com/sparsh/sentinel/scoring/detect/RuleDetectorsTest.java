package com.sparsh.sentinel.scoring.detect;

import org.junit.jupiter.api.Test;

import static com.sparsh.sentinel.scoring.TestEvents.PROPERTIES;
import static com.sparsh.sentinel.scoring.TestEvents.cashDeposit;
import static com.sparsh.sentinel.scoring.TestEvents.event;
import static org.assertj.core.api.Assertions.assertThat;

/** The stateless rules. Thresholds are where these go wrong, so the tests sit on the edges. */
class RuleDetectorsTest {

    private final LargeCashDetector largeCash = new LargeCashDetector(PROPERTIES);
    private final HighRiskJurisdictionDetector jurisdiction = new HighRiskJurisdictionDetector(PROPERTIES);

    @Test
    void largeCashFiresAtTheThresholdAndNotACentBelow() {
        assertThat(largeCash.evaluate(cashDeposit("9999.99"))).isEmpty();
        assertThat(largeCash.evaluate(cashDeposit("10000.00")))
                .hasValueSatisfying(f -> {
                    assertThat(f.rule()).isEqualTo(Finding.Rule.LARGE_CASH);
                    assertThat(f.reason()).contains("deposit").contains("10000.00");
                });
    }

    @Test
    void largeCashCoversWithdrawalsButNotTransfers() {
        assertThat(largeCash.evaluate(event("DEBIT", "CASH", "15000", null))).isPresent();
        assertThat(largeCash.evaluate(event("DEBIT", "TRANSFER", "250000", "DE"))).isEmpty();
    }

    @Test
    void jurisdictionFiresOnAListedCountryInEitherDirection() {
        assertThat(jurisdiction.evaluate(event("DEBIT", "TRANSFER", "50", "IR")))
                .hasValueSatisfying(f -> assertThat(f.reason()).startsWith("Sent").contains("to a counterparty in IR"));
        assertThat(jurisdiction.evaluate(event("CREDIT", "TRANSFER", "50", "KP")))
                .hasValueSatisfying(f -> assertThat(f.reason()).startsWith("Received"));
    }

    @Test
    void jurisdictionIgnoresUnlistedAndMissingCountries() {
        assertThat(jurisdiction.evaluate(event("DEBIT", "TRANSFER", "90000", "DE"))).isEmpty();
        assertThat(jurisdiction.evaluate(event("CREDIT", "CASH", "90000", null))).isEmpty();
    }
}
