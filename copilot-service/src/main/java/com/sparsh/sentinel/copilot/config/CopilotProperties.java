package com.sparsh.sentinel.copilot.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Retrieval tuning. These are the knobs that decide answer quality, so they are configuration
 * rather than constants buried in a service.
 *
 * @param chunkSizeTokens     target chunk size. Too small and a chunk loses the context that
 *                            makes it meaningful; too large and the embedding blurs across
 *                            several unrelated clauses.
 * @param minChunkSizeChars   chunks shorter than this are discarded as fragments.
 * @param topK                how many chunks are put in front of the model.
 * @param similarityThreshold cosine similarity below which a chunk is treated as noise and
 *                            dropped. Without it the store always returns topK results, however
 *                            irrelevant, and the model answers from whatever it was handed.
 */
@Validated
@ConfigurationProperties(prefix = "sentinel.copilot")
public record CopilotProperties(

        @Min(100) @Max(2000)
        int chunkSizeTokens,

        @Min(0)
        int minChunkSizeChars,

        @Min(1) @Max(50)
        int topK,

        @Min(0) @Max(1)
        double similarityThreshold
) {
}
