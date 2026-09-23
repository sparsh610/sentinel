package com.sparsh.sentinel.scoring.detect;

import com.sparsh.sentinel.scoring.config.ScoringProperties;
import com.sparsh.sentinel.scoring.event.TransactionEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/** Cash at or above the threshold, in either direction. */
@Component
public class LargeCashDetector implements Detector {

    private static final BigDecimal SCORE = new BigDecimal("0.60");

    private final BigDecimal threshold;

    public LargeCashDetector(ScoringProperties properties) {
        this.threshold = properties.cashThreshold();
    }

    @Override
    public Optional<Finding> evaluate(TransactionEvent tx) {
        if (!tx.isCash() || tx.amount().compareTo(threshold) < 0) {
            return Optional.empty();
        }
        return Optional.of(new Finding(Finding.Rule.LARGE_CASH, SCORE,
                "Cash %s of %s %s is at or above the %s threshold".formatted(
                        tx.isCredit() ? "deposit" : "withdrawal",
                        tx.currency(), tx.amount().toPlainString(), threshold.toPlainString())));
    }
}
