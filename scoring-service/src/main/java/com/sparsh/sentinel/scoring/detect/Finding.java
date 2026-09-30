package com.sparsh.sentinel.scoring.detect;

import java.math.BigDecimal;

/**
 * @param rule   which detector fired.
 * @param score  in [0, 1]. For the rules and the anomaly model this is a fixed weight, not a
 *               probability; for the classifier it is the model's output score. One column, so
 *               the queue sorts on it.
 * @param reason the sentence the analyst reads. Has to name the facts that triggered it.
 */
public record Finding(Rule rule, BigDecimal score, String reason) {

    public enum Rule {
        LARGE_CASH,
        STRUCTURING,
        HIGH_RISK_JURISDICTION,
        /** The supervised model: resembles labelled laundering. */
        ML_CLASSIFIER,
        /** The unsupervised models: unusual for the customer's peer segment. */
        ML_ANOMALY
    }
}
