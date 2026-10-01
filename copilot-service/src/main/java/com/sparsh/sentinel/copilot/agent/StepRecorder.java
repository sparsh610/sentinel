package com.sparsh.sentinel.copilot.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Runs each tool call of one investigation, enforces the step limit, and writes every call -
 * including failed and refused ones - to the execution trace as it happens.
 *
 * <p>The limit is enforced here, around the tools, rather than trusted to the planner. Whatever
 * the planner asks for, a call past the limit is not made; it is recorded as REFUSED. The last
 * step is reserved for the case note, so evidence-gathering can never crowd it out.
 */
final class StepRecorder {

    static final String DRAFT_CASE_NOTE = "draftCaseNote";
    private static final int MAX_STORED_CHARS = 8_000;
    private static final Logger log = LoggerFactory.getLogger(StepRecorder.class);

    private final Investigation investigation;
    private final int maxSteps;
    private final InvestigationStepRepository steps;
    private final ObjectMapper json;
    private final Clock clock;
    private int used;
    private int recorded;

    StepRecorder(Investigation investigation, int maxSteps, InvestigationStepRepository steps,
                 ObjectMapper json, Clock clock) {
        this.investigation = investigation;
        this.maxSteps = maxSteps;
        this.steps = steps;
        this.json = json;
        this.clock = clock;
    }

    /** Whether one more evidence tool may run, with the note's step still kept free. */
    boolean evidenceBudgetLeft() {
        return used < maxSteps - 1;
    }

    /**
     * Runs one tool call and records it.
     *
     * @return the result, or empty when the call failed or was refused - both are in the trace
     */
    <T> Optional<T> run(String tool, Object input, Supplier<T> call) {
        boolean allowed = DRAFT_CASE_NOTE.equals(tool) ? used < maxSteps : evidenceBudgetLeft();
        Instant startedAt = clock.instant();
        if (!allowed) {
            save(tool, input, "Step limit of " + maxSteps + " reached", InvestigationStep.Status.REFUSED, 0, startedAt);
            return Optional.empty();
        }

        used++;
        long t0 = System.nanoTime();
        try {
            T result = call.get();
            save(tool, input, result, InvestigationStep.Status.OK, elapsedMs(t0), startedAt);
            return Optional.ofNullable(result);
        } catch (RuntimeException e) {
            log.warn("Investigation {} step {} failed: {}", investigation.getId(), tool, e.getMessage());
            save(tool, input, e.getClass().getSimpleName() + ": " + e.getMessage(),
                    InvestigationStep.Status.ERROR, elapsedMs(t0), startedAt);
            return Optional.empty();
        }
    }

    int used() {
        return used;
    }

    private void save(String tool, Object input, Object output, InvestigationStep.Status status,
                      int durationMs, Instant startedAt) {
        recorded++;
        steps.save(new InvestigationStep(investigation, recorded, tool, toJson(input),
                output == null ? null : toJson(output), status, durationMs, startedAt));
    }

    private String toJson(Object value) {
        String text;
        if (value instanceof String s) {
            text = s;
        } else {
            try {
                text = json.writeValueAsString(value);
            } catch (JsonProcessingException e) {
                text = String.valueOf(value);
            }
        }
        return text.length() <= MAX_STORED_CHARS ? text : text.substring(0, MAX_STORED_CHARS) + " …[truncated]";
    }

    private static int elapsedMs(long t0) {
        return (int) ((System.nanoTime() - t0) / 1_000_000);
    }
}
