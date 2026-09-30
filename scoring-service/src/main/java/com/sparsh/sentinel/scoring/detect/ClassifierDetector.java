package com.sparsh.sentinel.scoring.detect;

import com.sparsh.sentinel.scoring.event.TransactionEvent;
import com.sparsh.sentinel.scoring.model.FeatureBuilder;
import com.sparsh.sentinel.scoring.model.ModelFeatures;
import com.sparsh.sentinel.scoring.model.ModelProperties;
import com.sparsh.sentinel.scoring.model.Models;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The supervised model (XGBoost): how much this transaction looks like laundering that was
 * already caught and labelled. Strong on known typologies, blind to new ones - that is the
 * {@link AnomalyDetector}'s side.
 *
 * <p>Its score is the model's output, so unlike a rule's fixed weight it ranks alerts against
 * each other. It is not a calibrated probability: training weighted the rare laundering rows
 * about a thousand times up, which pushes every output towards 1. So the analyst reads "score",
 * never a percentage.
 */
@Component
public class ClassifierDetector implements Detector {

    private static final String PROBABILITIES = "probabilities";
    private static final int LAUNDERING = 1;

    private final Models models;
    private final FeatureBuilder features;
    private final BigDecimal threshold;

    public ClassifierDetector(Models models, FeatureBuilder features, ModelProperties properties) {
        this.models = models;
        this.features = features;
        this.threshold = properties.classifierThreshold();
    }

    @Override
    public Optional<Finding> evaluate(TransactionEvent tx) {
        if (models.loaded().isEmpty() || !ModelFeatures.supports(tx)) {
            return Optional.empty();
        }

        float[] x = features.build(tx);
        float probability = models.loaded().get().classifier().floats(x, PROBABILITIES)[LAUNDERING];
        BigDecimal score = BigDecimal.valueOf(probability).setScale(4, RoundingMode.HALF_UP);
        if (score.compareTo(threshold) < 0) {
            return Optional.empty();
        }

        return Optional.of(new Finding(Finding.Rule.ML_CLASSIFIER, score,
                "Resembles labelled laundering: model score %s (alerts at %s)%s".formatted(
                        score.toPlainString(), threshold.toPlainString(), facts(tx, x))));
    }

    /** The behavioural facts behind the score that an analyst can check against the ledger. */
    private static String facts(TransactionEvent tx, float[] x) {
        List<String> facts = new ArrayList<>();
        if (x[ModelFeatures.NAMES.indexOf("is_new_counterparty")] == 1) {
            facts.add("first transaction with " + tx.counterpartyName());
        }
        long newCounterparties = Math.round(Math.expm1(x[ModelFeatures.NAMES.indexOf("log_new_counterparties_24h")]));
        if (newCounterparties > 1) {
            facts.add(newCounterparties + " new counterparties in 24 hours");
        }
        long count24h = Math.round(Math.expm1(x[ModelFeatures.NAMES.indexOf("log_count_24h")]));
        if (count24h > 1) {
            facts.add(count24h + " transactions in 24 hours");
        }
        return facts.isEmpty() ? "" : "; " + String.join(", ", facts);
    }
}
