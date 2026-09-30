package com.sparsh.sentinel.scoring.model;

import com.sparsh.sentinel.scoring.event.TransactionEvent;
import com.sparsh.sentinel.scoring.score.CustomerWindow;
import com.sparsh.sentinel.scoring.score.ScoredTransactionRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Builds a transaction's model features from the event and the customer's ledger history.
 *
 * <p>Each model detector calls this for itself, so a transaction costs two copies of one indexed
 * query. Sharing the result would mean coupling the detectors or caching per transaction; at
 * this volume the query is cheaper than either.
 */
@Component
public class FeatureBuilder {

    private static final Duration DAY = Duration.ofHours(24);
    private static final Duration WEEK = Duration.ofDays(7);

    private final ScoredTransactionRepository ledger;

    public FeatureBuilder(ScoredTransactionRepository ledger) {
        this.ledger = ledger;
    }

    public float[] build(TransactionEvent tx) {
        Instant at = tx.bookedAt();
        String counterparty = tx.counterpartyName() == null ? "" : tx.counterpartyName();
        CustomerWindow window = ledger.customerWindow(
                tx.customerId(), tx.transactionId(), counterparty, at, at.minus(DAY), at.minus(WEEK));
        return ModelFeatures.of(tx, window);
    }
}
