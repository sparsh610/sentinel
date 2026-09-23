package com.sparsh.sentinel.scoring.detect;

import com.sparsh.sentinel.scoring.config.ScoringProperties;
import com.sparsh.sentinel.scoring.event.TransactionEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

/** Any payment to or from a high-risk jurisdiction, whatever the amount. */
@Component
public class HighRiskJurisdictionDetector implements Detector {

    private static final BigDecimal SCORE = new BigDecimal("0.75");

    private final Set<String> countries;

    public HighRiskJurisdictionDetector(ScoringProperties properties) {
        this.countries = Set.copyOf(properties.highRiskCountries());
    }

    @Override
    public Optional<Finding> evaluate(TransactionEvent tx) {
        String country = tx.counterpartyCountry();
        if (country == null || !countries.contains(country)) {
            return Optional.empty();
        }
        return Optional.of(new Finding(Finding.Rule.HIGH_RISK_JURISDICTION, SCORE,
                "%s %s %s %s a counterparty in %s, a FATF high-risk jurisdiction".formatted(
                        tx.isCredit() ? "Received" : "Sent",
                        tx.currency(), tx.amount().toPlainString(),
                        tx.isCredit() ? "from" : "to", country)));
    }
}
