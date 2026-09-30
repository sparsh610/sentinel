package com.sparsh.sentinel.scoring.model;

import com.sparsh.sentinel.scoring.event.TransactionEvent;
import com.sparsh.sentinel.scoring.score.CustomerWindow;

import java.time.ZoneOffset;
import java.util.List;

/**
 * The Java half of the feature contract. {@code ml/sentinel_features.py} is the other half.
 *
 * <p>The names and their order must match the {@code .features.json} manifest beside each model:
 * the models take a bare array of floats, so a swapped pair would not fail - it would produce
 * plausible, wrong scores. {@link OnnxModel} refuses to load a model whose manifest differs, and
 * a test checks the committed manifests.
 */
public final class ModelFeatures {

    public static final List<String> NAMES = List.of(
            "log_amount_eur",
            "is_credit",
            "channel_cash",
            "channel_card",
            "channel_transfer",
            "hour_of_day",
            "log_count_24h",
            "log_sum_24h",
            "log_credit_count_24h",
            "log_cash_count_24h",
            "log_count_7d",
            "log_amount_vs_7d",
            "is_new_counterparty",
            "log_new_counterparties_24h");

    private ModelFeatures() {
    }

    /** The models were trained on EUR amounts; anything else is not scored by them. */
    public static boolean supports(TransactionEvent tx) {
        return "EUR".equals(tx.currency());
    }

    public static float[] of(TransactionEvent tx, CustomerWindow window) {
        double amount = tx.amount().doubleValue();
        long earlier = window.getCount7d() - 1;
        double meanEarlier = (window.getSum7d().doubleValue() - amount) / Math.max(earlier, 1);
        boolean newCounterparty = tx.counterpartyName() != null && !window.getCounterpartySeenBefore();

        return new float[] {
                (float) Math.log1p(amount),
                tx.isCredit() ? 1 : 0,
                tx.isCash() ? 1 : 0,
                "CARD".equals(tx.channel()) ? 1 : 0,
                "TRANSFER".equals(tx.channel()) ? 1 : 0,
                tx.bookedAt().atZone(ZoneOffset.UTC).getHour(),
                (float) Math.log1p(window.getCount24h()),
                (float) Math.log1p(window.getSum24h().doubleValue()),
                (float) Math.log1p(window.getCreditCount24h()),
                (float) Math.log1p(window.getCashCount24h()),
                (float) Math.log1p(window.getCount7d()),
                earlier > 0 ? (float) (Math.log1p(amount) - Math.log1p(meanEarlier)) : 0f,
                newCounterparty ? 1 : 0,
                (float) Math.log1p(window.getNewCounterparties24h()),
        };
    }
}
