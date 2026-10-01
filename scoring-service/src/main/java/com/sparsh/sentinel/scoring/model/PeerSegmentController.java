package com.sparsh.sentinel.scoring.model;

import com.sparsh.sentinel.scoring.event.TransactionEvent;
import com.sparsh.sentinel.scoring.score.ScoredTransaction;
import com.sparsh.sentinel.scoring.score.ScoredTransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Where a scored transaction sits among its peers - the investigation agent's {@code peerSegment}
 * tool. The anomaly detector only says something when a transaction is unusual; an investigator
 * also wants to hear "this is ordinary for its segment", which is evidence too.
 *
 * <p>The features are rebuilt from the ledger as of the transaction's booking time, so the
 * answer is the same whenever it is asked, not shifted by transactions that came later.
 */
@RestController
public class PeerSegmentController {

    private final ScoredTransactionRepository ledger;
    private final FeatureBuilder features;
    private final Models models;

    public PeerSegmentController(ScoredTransactionRepository ledger, FeatureBuilder features, Models models) {
        this.ledger = ledger;
        this.features = features;
        this.models = models;
    }

    /**
     * @param segment       KMeans peer segment.
     * @param anomalyScore  Isolation Forest score; lower is more unusual.
     * @param threshold     the segment's own threshold - its most unusual 0.1% fall below it.
     * @param unusual       whether the score is below the threshold.
     */
    public record PeerSegmentView(UUID transactionId, long segment, double anomalyScore,
                                  double threshold, boolean unusual) {
    }

    @GetMapping("/api/transactions/{transactionId}/peer-segment")
    @Transactional(readOnly = true)
    public PeerSegmentView peerSegment(@PathVariable UUID transactionId) {
        Models.Loaded loaded = models.loaded().orElseThrow(() -> new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "No models loaded - run the notebooks in ml/notebooks"));
        ScoredTransaction scored = ledger.findById(transactionId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Transaction " + transactionId + " has not been scored"));
        TransactionEvent tx = asEvent(scored);
        if (!ModelFeatures.supports(tx)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The models only score EUR; this transaction is in " + tx.currency());
        }

        float[] x = features.build(tx);
        long segment = loaded.segments().label(x, "label");
        float score = loaded.anomaly().floats(x, "scores")[0];
        float threshold = loaded.anomalyThresholds().getOrDefault(segment, Float.NEGATIVE_INFINITY);
        return new PeerSegmentView(transactionId, segment, score, threshold, score < threshold);
    }

    private static TransactionEvent asEvent(ScoredTransaction t) {
        return new TransactionEvent(TransactionEvent.SUPPORTED_SCHEMA_VERSION, t.getTransactionId(), null,
                t.getCustomerId(), null, null, t.getDirection(), t.getChannel(), t.getAmount(), t.getCurrency(),
                t.getCounterpartyName(), t.getCounterpartyCountry(), t.getBookedAt());
    }
}
