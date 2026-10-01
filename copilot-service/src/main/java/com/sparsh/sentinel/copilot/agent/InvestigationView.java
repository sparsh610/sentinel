package com.sparsh.sentinel.copilot.agent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An investigation as the console shows it: the case, its execution trace, and its decisions. */
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
        List<DecisionView> decisions
) {

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
                        d.getComment(), d.getDecidedAt())).toList());
    }
}
