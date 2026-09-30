package com.sparsh.sentinel.scoring.detect;

import com.sparsh.sentinel.scoring.event.TransactionEvent;
import com.sparsh.sentinel.scoring.model.FeatureBuilder;
import com.sparsh.sentinel.scoring.model.ModelFeatures;
import com.sparsh.sentinel.scoring.model.ModelProperties;
import com.sparsh.sentinel.scoring.model.Models;
import com.sparsh.sentinel.scoring.model.OnnxModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.sparsh.sentinel.scoring.TestEvents.BOOKED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModelDetectorsTest {

    private static final ModelProperties PROPERTIES =
            new ModelProperties("unused", new BigDecimal("0.98"), new BigDecimal("0.40"));

    @Mock private Models models;
    @Mock private FeatureBuilder features;
    @Mock private OnnxModel classifier;
    @Mock private OnnxModel anomaly;
    @Mock private OnnxModel segments;

    private final float[] x = new float[ModelFeatures.NAMES.size()];

    @BeforeEach
    void modelsAreLoaded() {
        when(models.loaded()).thenReturn(Optional.of(
                new Models.Loaded(classifier, anomaly, segments, Map.of(0L, -0.12f, 2L, -0.23f))));
        when(features.build(any())).thenReturn(x);
    }

    @Test
    void classifierRaisesAFindingAtItsThresholdAndCarriesTheProbability() {
        x[ModelFeatures.NAMES.indexOf("is_new_counterparty")] = 1;
        x[ModelFeatures.NAMES.indexOf("log_new_counterparties_24h")] = (float) Math.log1p(4);
        when(classifier.floats(eq(x), eq("probabilities"))).thenReturn(new float[] {0.01f, 0.99f});

        Finding finding = classifier().evaluate(transfer("EUR")).orElseThrow();

        assertThat(finding.rule()).isEqualTo(Finding.Rule.ML_CLASSIFIER);
        assertThat(finding.score()).isEqualByComparingTo("0.99");
        assertThat(finding.reason())
                .contains("model score 0.9900 (alerts at 0.98)")
                .contains("first transaction with Kish Trading Co.")
                .contains("4 new counterparties in 24 hours");
    }

    @Test
    void classifierStaysQuietBelowItsThreshold() {
        when(classifier.floats(eq(x), eq("probabilities"))).thenReturn(new float[] {0.03f, 0.97f});

        assertThat(classifier().evaluate(transfer("EUR"))).isEmpty();
    }

    @Test
    void anomalyIsJudgedAgainstItsOwnSegmentsThreshold() {
        when(segments.label(eq(x), eq("label"))).thenReturn(2L);
        // Would be anomalous in segment 0 (threshold -0.12), but segment 2's own is -0.23.
        when(anomaly.floats(eq(x), eq("scores"))).thenReturn(new float[] {-0.20f});
        assertThat(anomaly().evaluate(transfer("EUR"))).isEmpty();

        when(anomaly.floats(eq(x), eq("scores"))).thenReturn(new float[] {-0.25f});
        Finding finding = anomaly().evaluate(transfer("EUR")).orElseThrow();

        assertThat(finding.rule()).isEqualTo(Finding.Rule.ML_ANOMALY);
        assertThat(finding.score()).isEqualByComparingTo("0.40");
        assertThat(finding.reason()).contains("peer segment 2").contains("-0.250").contains("-0.230");
    }

    @Test
    void withoutExportedModelsOnlyTheRulesRun() {
        when(models.loaded()).thenReturn(Optional.empty());

        assertThat(classifier().evaluate(transfer("EUR"))).isEmpty();
        assertThat(anomaly().evaluate(transfer("EUR"))).isEmpty();
        verifyNoInteractions(features);
    }

    @Test
    void nonEuroTransactionsAreLeftToTheRules() {
        assertThat(classifier().evaluate(transfer("USD"))).isEmpty();
        assertThat(anomaly().evaluate(transfer("USD"))).isEmpty();
        verifyNoInteractions(features);
    }

    private ClassifierDetector classifier() {
        return new ClassifierDetector(models, features, PROPERTIES);
    }

    private AnomalyDetector anomaly() {
        return new AnomalyDetector(models, features, PROPERTIES);
    }

    private static TransactionEvent transfer(String currency) {
        return new TransactionEvent(1, UUID.randomUUID(), "REF", "C-10001", "RETAIL", "LOW",
                "DEBIT", "TRANSFER", new BigDecimal("8000"), currency, "Kish Trading Co.", "AE", BOOKED);
    }
}
