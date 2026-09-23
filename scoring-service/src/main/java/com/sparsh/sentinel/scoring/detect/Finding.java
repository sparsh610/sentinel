package com.sparsh.sentinel.scoring.detect;

import java.math.BigDecimal;

/**
 * @param rule   which detector fired.
 * @param score  in [0, 1]. For rules this is a fixed weight per rule, not a probability - it
 *               exists so the queue can be sorted on one column once the models, which do
 *               produce probabilities, add findings of their own.
 * @param reason the sentence the analyst reads. Has to name the facts that triggered it.
 */
public record Finding(Rule rule, BigDecimal score, String reason) {

    public enum Rule {
        LARGE_CASH,
        STRUCTURING,
        HIGH_RISK_JURISDICTION
    }
}
