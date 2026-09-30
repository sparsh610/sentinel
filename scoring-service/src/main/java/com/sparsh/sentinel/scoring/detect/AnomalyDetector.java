package com.sparsh.sentinel.scoring.detect;

import com.sparsh.sentinel.scoring.event.TransactionEvent;
import com.sparsh.sentinel.scoring.model.FeatureBuilder;
import com.sparsh.sentinel.scoring.model.ModelFeatures;
import com.sparsh.sentinel.scoring.model.ModelProperties;
import com.sparsh.sentinel.scoring.model.Models;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The unsupervised pair: KMeans places the transaction in a peer segment, and the Isolation
 * Forest's score is judged against that segment's own threshold. A large transfer is ordinary in
 * a segment of large transfers and unusual in a segment of card payments; one global threshold
 * would flag the first and miss the second.
 *
 * <p>It uses no labels, so it can surface a pattern nobody has named yet - and for the same
 * reason it is noisy. It carries a fixed, low score so it ranks below the rules.
 */
@Component
public class AnomalyDetector implements Detector {

    private static final String SEGMENT = "label";
    private static final String SCORE = "scores";

    private final Models models;
    private final FeatureBuilder features;
    private final BigDecimal findingScore;

    public AnomalyDetector(Models models, FeatureBuilder features, ModelProperties properties) {
        this.models = models;
        this.features = features;
        this.findingScore = properties.anomalyScore();
    }

    @Override
    public Optional<Finding> evaluate(TransactionEvent tx) {
        if (models.loaded().isEmpty() || !ModelFeatures.supports(tx)) {
            return Optional.empty();
        }
        Models.Loaded loaded = models.loaded().get();

        float[] x = features.build(tx);
        long segment = loaded.segments().label(x, SEGMENT);
        float score = loaded.anomaly().floats(x, SCORE)[0];
        Float threshold = loaded.anomalyThresholds().get(segment);
        if (threshold == null || score >= threshold) {
            return Optional.empty();
        }

        return Optional.of(new Finding(Finding.Rule.ML_ANOMALY, findingScore,
                "Unusual for its peer segment %d: anomaly score %.3f is below the segment's threshold %.3f"
                        .formatted(segment, score, threshold)));
    }
}
