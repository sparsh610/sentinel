package com.sparsh.sentinel.scoring.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

/**
 * Detection thresholds. Configuration rather than constants, because in a bank these come out
 * of the institution's risk assessment and change without a code release.
 *
 * @param topic              where transactions arrive.
 * @param deadLetterTopic    where a record goes when it cannot be scored at all.
 * @param topicPartitions    partitions of the dead-letter topic. Must be at least the source
 *                           topic's: a failed record keeps its partition number.
 * @param cashThreshold      cash at or above this raises an alert on its own.
 * @param structuring        the structuring pattern.
 * @param highRiskCountries  ISO 3166-1 alpha-2 codes; any payment to or from one raises an alert.
 */
@Validated
@ConfigurationProperties(prefix = "sentinel.scoring")
public record ScoringProperties(

        @NotBlank
        String topic,

        @NotBlank
        String deadLetterTopic,

        @Min(1)
        int topicPartitions,

        @NotNull @Positive
        BigDecimal cashThreshold,

        @Valid @NotNull
        Structuring structuring,

        @NotNull
        Set<String> highRiskCountries
) {

    /**
     * @param bandFloor lower edge of the "just under the threshold" band.
     * @param minCount  deposits in the band, within the window, that make a pattern.
     * @param window    how far back from each deposit to look.
     */
    public record Structuring(

            @NotNull @Positive
            BigDecimal bandFloor,

            @Min(2)
            int minCount,

            @NotNull
            Duration window
    ) {
    }
}
