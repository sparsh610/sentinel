package com.sparsh.sentinel.scoring.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * @param dir                 where the notebooks in {@code ml/} exported the {@code .onnx} files.
 *                            A string, not a {@code Path}: Spring binds a {@code Path} through its
 *                            resource loader, which rejects the {@code ../} this default needs.
 * @param classifierThreshold the supervised model's score at or above which it raises a
 *                            finding. A business decision - how many alerts the team can work per
 *                            true case - read off the precision/recall table in notebook 01.
 * @param anomalyScore        the fixed score an anomaly finding carries. The Isolation Forest's
 *                            raw output is not a probability, and on its own it is a weak signal,
 *                            so it is weighted below every rule.
 */
@Validated
@ConfigurationProperties(prefix = "sentinel.scoring.models")
public record ModelProperties(

        @NotBlank
        String dir,

        @NotNull @DecimalMin("0") @DecimalMax("1")
        BigDecimal classifierThreshold,

        @NotNull @DecimalMin("0") @DecimalMax("1")
        BigDecimal anomalyScore
) {
}
