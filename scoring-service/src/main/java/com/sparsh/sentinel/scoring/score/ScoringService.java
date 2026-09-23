package com.sparsh.sentinel.scoring.score;

import com.sparsh.sentinel.scoring.alert.Alert;
import com.sparsh.sentinel.scoring.alert.AlertRepository;
import com.sparsh.sentinel.scoring.detect.Detector;
import com.sparsh.sentinel.scoring.detect.Finding;
import com.sparsh.sentinel.scoring.event.TransactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Scores one transaction: records it, runs every detector, and raises at most one alert carrying
 * all of their findings.
 *
 * <p>All of it is one database transaction. The Kafka offset is committed only after this
 * returns, so a crash anywhere in here means the record is delivered again - and the ledger
 * insert at the top is what turns that redelivery into a no-op instead of a duplicate alert.
 */
@Service
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

    private final ScoredTransactionRepository ledger;
    private final AlertRepository alerts;
    private final List<Detector> detectors;
    private final Clock clock;

    public ScoringService(ScoredTransactionRepository ledger,
                          AlertRepository alerts,
                          List<Detector> detectors,
                          Clock clock) {
        this.ledger = ledger;
        this.alerts = alerts;
        this.detectors = List.copyOf(detectors);
        this.clock = clock;
    }

    /** @return the alert raised, if any. Empty for a clean transaction and for a redelivery. */
    @Transactional
    public Optional<Alert> score(TransactionEvent tx) {
        Instant now = clock.instant();

        if (ledger.recordIfAbsent(ScoredTransaction.of(tx, now)) == 0) {
            log.debug("Transaction {} already scored; redelivery ignored", tx.transactionId());
            return Optional.empty();
        }

        List<Finding> findings = detectors.stream()
                .map(detector -> detector.evaluate(tx))
                .flatMap(Optional::stream)
                .toList();

        if (findings.isEmpty()) {
            return Optional.empty();
        }

        Alert alert = alerts.save(Alert.raise(tx, findings, now));
        log.info("Alert {} for customer {}: {}", alert.getId(), tx.customerId(),
                findings.stream().map(f -> f.rule().name()).toList());
        return Optional.of(alert);
    }
}
