package com.sparsh.sentinel.scoring.detect;

import com.sparsh.sentinel.scoring.alert.AlertRepository;
import com.sparsh.sentinel.scoring.config.ScoringProperties;
import com.sparsh.sentinel.scoring.event.TransactionEvent;
import com.sparsh.sentinel.scoring.score.ScoredTransactionRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Structuring ("smurfing"): splitting cash into several deposits that each stay just under the
 * reporting threshold.
 *
 * <p>No single deposit is suspicious, which is the whole point of the technique - this is the
 * rule that needs history. It counts the customer's cash deposits inside the band over the
 * trailing window, the current one included.
 *
 * <p>It fires once per pattern, not once per deposit. Without that, a customer who makes a
 * fifth deposit gets a third and fourth alert for what an analyst would treat as one case.
 */
@Component
public class StructuringDetector implements Detector {

    private static final BigDecimal SCORE = new BigDecimal("0.85");

    private final ScoredTransactionRepository ledger;
    private final AlertRepository alerts;
    private final ScoringProperties properties;

    public StructuringDetector(ScoredTransactionRepository ledger,
                               AlertRepository alerts,
                               ScoringProperties properties) {
        this.ledger = ledger;
        this.alerts = alerts;
        this.properties = properties;
    }

    @Override
    public Optional<Finding> evaluate(TransactionEvent tx) {
        BigDecimal floor = properties.structuring().bandFloor();
        BigDecimal ceiling = properties.cashThreshold();

        if (!tx.isCash() || !tx.isCredit()
                || tx.amount().compareTo(floor) < 0 || tx.amount().compareTo(ceiling) >= 0) {
            return Optional.empty();
        }

        Instant since = tx.bookedAt().minus(properties.structuring().window());
        long inBand = ledger.countCashDepositsInBand(tx.customerId(), floor, ceiling, since, tx.bookedAt());

        if (inBand < properties.structuring().minCount()) {
            return Optional.empty();
        }
        if (alerts.existsFindingForCustomerSince(tx.customerId(), Finding.Rule.STRUCTURING, since)) {
            return Optional.empty();
        }

        return Optional.of(new Finding(Finding.Rule.STRUCTURING, SCORE,
                "%d cash deposits between %s and %s %s within %d hours".formatted(
                        inBand, floor.toPlainString(), ceiling.toPlainString(), tx.currency(),
                        properties.structuring().window().toHours())));
    }
}
