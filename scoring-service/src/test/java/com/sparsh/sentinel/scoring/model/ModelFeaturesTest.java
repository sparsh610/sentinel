package com.sparsh.sentinel.scoring.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparsh.sentinel.scoring.event.TransactionEvent;
import com.sparsh.sentinel.scoring.score.CustomerWindow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ModelFeaturesTest {

    /** Maven runs tests from the module directory. */
    static final Path MODELS = Path.of("..", "ml", "models");

    private static final Instant BOOKED = Instant.parse("2026-09-23T14:30:00Z");

    /**
     * The manifests are committed even though the models are not, so this runs on every build:
     * a feature added on one side only fails here, before any model is trained on it.
     */
    @ParameterizedTest
    @ValueSource(strings = {"fraud_xgboost", "anomaly_isoforest", "segments_kmeans"})
    void javaBuildsTheFeaturesInTheOrderTheModelsWereTrainedOn(String model) throws IOException {
        List<String> manifest = new ObjectMapper().readValue(
                MODELS.resolve(model + ".features.json").toFile(), new TypeReference<>() { });

        assertThat(ModelFeatures.NAMES).containsExactlyElementsOf(manifest);
    }

    @Test
    void computesEachFeatureAsTheTrainingCodeDoes() {
        TransactionEvent tx = transfer("CREDIT", "2500", "Kish Trading Co.");
        // Four transactions in 7 days including this one, totalling 4000: the three earlier
        // ones average 500.
        Window window = new Window(3, "3500", 2, 0, 4, "4000", 2, false);

        float[] x = ModelFeatures.of(tx, window);

        assertThat(x).hasSize(ModelFeatures.NAMES.size());
        assertThat(feature(x, "log_amount_eur")).isCloseTo((float) Math.log1p(2500), within(1e-6f));
        assertThat(feature(x, "is_credit")).isEqualTo(1f);
        assertThat(feature(x, "channel_transfer")).isEqualTo(1f);
        assertThat(feature(x, "channel_cash")).isZero();
        assertThat(feature(x, "channel_card")).isZero();
        assertThat(feature(x, "hour_of_day")).isEqualTo(14f);
        assertThat(feature(x, "log_count_24h")).isCloseTo((float) Math.log1p(3), within(1e-6f));
        assertThat(feature(x, "log_sum_24h")).isCloseTo((float) Math.log1p(3500), within(1e-6f));
        assertThat(feature(x, "log_credit_count_24h")).isCloseTo((float) Math.log1p(2), within(1e-6f));
        assertThat(feature(x, "log_cash_count_24h")).isZero();
        assertThat(feature(x, "log_count_7d")).isCloseTo((float) Math.log1p(4), within(1e-6f));
        assertThat(feature(x, "log_amount_vs_7d"))
                .isCloseTo((float) (Math.log1p(2500) - Math.log1p(500)), within(1e-6f));
        assertThat(feature(x, "is_new_counterparty")).isEqualTo(1f);
        assertThat(feature(x, "log_new_counterparties_24h")).isCloseTo((float) Math.log1p(2), within(1e-6f));
    }

    @Test
    void aCustomersFirstTransactionHasNothingToCompareItsAmountWith() {
        float[] x = ModelFeatures.of(transfer("DEBIT", "900", "Acme"), new Window(1, "900", 0, 0, 1, "900", 1, false));

        assertThat(feature(x, "log_amount_vs_7d")).isZero();
    }

    @Test
    void cashHasNoCounterpartySoItIsNeverANewOne() {
        TransactionEvent cash = new TransactionEvent(1, UUID.randomUUID(), "REF", "C-1", "RETAIL", "LOW",
                "CREDIT", "CASH", new BigDecimal("400"), "EUR", null, null, BOOKED);

        float[] x = ModelFeatures.of(cash, new Window(1, "400", 1, 1, 1, "400", 0, false));

        assertThat(feature(x, "is_new_counterparty")).isZero();
        assertThat(feature(x, "channel_cash")).isEqualTo(1f);
    }

    @Test
    void aKnownCounterpartyIsNotNew() {
        float[] x = ModelFeatures.of(transfer("DEBIT", "50", "Acme"), new Window(2, "100", 0, 0, 2, "100", 0, true));

        assertThat(feature(x, "is_new_counterparty")).isZero();
    }

    @Test
    void onlyEuroAmountsAreScoredByTheModels() {
        assertThat(ModelFeatures.supports(transfer("DEBIT", "50", "Acme"))).isTrue();
        TransactionEvent dollars = new TransactionEvent(1, UUID.randomUUID(), "REF", "C-1", "RETAIL", "LOW",
                "DEBIT", "TRANSFER", new BigDecimal("50"), "USD", "Acme", "US", BOOKED);
        assertThat(ModelFeatures.supports(dollars)).isFalse();
    }

    private static float feature(float[] x, String name) {
        return x[ModelFeatures.NAMES.indexOf(name)];
    }

    private static TransactionEvent transfer(String direction, String amount, String counterparty) {
        return new TransactionEvent(1, UUID.randomUUID(), "REF", "C-1", "RETAIL", "LOW",
                direction, "TRANSFER", new BigDecimal(amount), "EUR", counterparty, "DE", BOOKED);
    }

    record Window(long count24h, String sum24h, long creditCount24h, long cashCount24h,
                  long count7d, String sum7d, long newCounterparties24h, boolean seenBefore)
            implements CustomerWindow {

        @Override public long getCount24h() { return count24h; }
        @Override public BigDecimal getSum24h() { return new BigDecimal(sum24h); }
        @Override public long getCreditCount24h() { return creditCount24h; }
        @Override public long getCashCount24h() { return cashCount24h; }
        @Override public long getCount7d() { return count7d; }
        @Override public BigDecimal getSum7d() { return new BigDecimal(sum7d); }
        @Override public long getNewCounterparties24h() { return newCounterparties24h; }
        @Override public boolean getCounterpartySeenBefore() { return seenBefore; }
    }
}
