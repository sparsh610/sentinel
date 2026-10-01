package com.sparsh.sentinel.copilot.agent;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param planner      who decides which evidence tools run: a fixed plan, or the local model.
 * @param maxSteps     hard cap on tool calls per investigation, the note included. A planner that
 *                     wants more is stopped, and the run says so in its trace.
 * @param model        the local Ollama model for the case note and the LLM planner. Small on
 *                     purpose: it has to fit next to everything else on a laptop.
 * @param scoringUrl   scoring-service, for the alert, its status and the peer segment.
 * @param ingestUrl    tx-ingest, for the customer's history.
 * @param historyLimit how many recent transactions the customerHistory tool reads.
 */
@Validated
@ConfigurationProperties(prefix = "sentinel.copilot.agent")
public record AgentProperties(

        @NotNull
        Planner planner,

        @Min(3) @Max(12)
        int maxSteps,

        @NotBlank
        String model,

        @NotBlank
        String scoringUrl,

        @NotBlank
        String ingestUrl,

        @Min(1) @Max(200)
        int historyLimit
) {

    public enum Planner {
        /** The evidence tools always run in the same order. Predictable and auditable. */
        FIXED,
        /** The local model chooses the evidence tools, under the same step limit and trace. */
        LLM
    }
}
