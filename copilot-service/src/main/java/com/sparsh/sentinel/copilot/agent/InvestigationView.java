package com.sparsh.sentinel.copilot.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An investigation as the console shows it: the case, its execution trace, and its decisions.
 * {@link #forModel()} is the same case as a model may see it.
 */
public record InvestigationView(
        UUID id,
        UUID alertId,
        UUID transactionId,
        String customerId,
        BigDecimal amount,
        String currency,
        AgentProperties.Planner planner,
        Investigation.Status status,
        String caseNote,
        Investigation.NoteSource noteSource,
        String failureReason,
        Instant startedAt,
        Instant finishedAt,
        List<StepView> steps,
        List<DecisionView> decisions,
        @JsonIgnore String caseNoteMasked
) {

    /**
     * For an MCP client: the note as the model wrote it, still masked, and the trace without the
     * tools' raw output - which has the names in it. The client can call the tools for masked
     * evidence of its own.
     */
    public InvestigationView forModel() {
        List<StepView> withheld = steps.stream().map(s -> new StepView(s.stepNo(), s.tool(), s.input(),
                s.output() == null ? null : "(withheld over MCP - call the tool for a masked copy)", s.status(),
                s.durationMs(), s.startedAt())).toList();
        return new InvestigationView(id, alertId, transactionId, customerId, amount, currency, planner, status,
                caseNoteMasked, noteSource, failureReason, startedAt, finishedAt, withheld, decisions, caseNoteMasked);
    }

    public record StepView(int stepNo, String tool, String input, String output, InvestigationStep.Status status,
                           int durationMs, Instant startedAt) {
    }

    public record DecisionView(String actor, InvestigationDecision.Role role, InvestigationDecision.Action action,
                               String comment, Instant decidedAt) {
    }

    static InvestigationView of(Investigation i) {
        return new InvestigationView(i.getId(), i.getAlertId(), i.getTransactionId(), i.getCustomerId(),
                i.getAmount(), i.getCurrency(), i.getPlanner(), i.getStatus(), i.getCaseNote(), i.getNoteSource(),
                i.getFailureReason(), i.getStartedAt(), i.getFinishedAt(),
                i.getSteps().stream().map(s -> new StepView(s.getStepNo(), s.getTool(), s.getInput(), s.getOutput(),
                        s.getStatus(), s.getDurationMs(), s.getStartedAt())).toList(),
                i.getDecisions().stream().map(d -> new DecisionView(d.getActor(), d.getRole(), d.getAction(),
                        d.getComment(), d.getDecidedAt())).toList(),
                i.getCaseNoteMasked());
    }
}
