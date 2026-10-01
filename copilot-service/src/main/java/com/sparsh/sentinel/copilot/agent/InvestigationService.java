package com.sparsh.sentinel.copilot.agent;

import com.sparsh.sentinel.copilot.agent.client.ScoringClient;
import com.sparsh.sentinel.copilot.agent.client.ScoringClient.AlertSnapshot;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Starts investigations and records the people's decisions on them.
 *
 * <p>The alert's status in scoring-service moves with the case: IN_REVIEW when an investigation
 * starts, CLOSED when the analyst closes it, ESCALATED only when a senior approver signs off. A
 * decision is saved in the same database transaction as that call, so a failed call to
 * scoring-service leaves no half-made decision behind.
 */
@Service
public class InvestigationService {

    private final InvestigationRepository investigations;
    private final ScoringClient scoring;
    private final InvestigationRunner runner;
    private final TaskExecutor executor;
    private final CaseNoteWriter noteWriter;
    private final AgentProperties properties;
    private final Clock clock;

    public InvestigationService(InvestigationRepository investigations, ScoringClient scoring,
                                InvestigationRunner runner, TaskExecutor executor, CaseNoteWriter noteWriter,
                                AgentProperties properties, Clock clock) {
        this.investigations = investigations;
        this.scoring = scoring;
        this.runner = runner;
        this.executor = executor;
        this.noteWriter = noteWriter;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Starts an investigation and returns at once; the agent runs in the background. An alert
     * that already has one in progress or awaiting a decision gets that one back, not a second.
     */
    public InvestigationView start(UUID alertId) {
        AlertSnapshot alert;
        try {
            alert = scoring.alert(alertId);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No alert " + alertId);
        }

        var active = investigations.findByAlertIdOrderByStartedAtDesc(alertId).stream()
                .filter(Investigation::isActive)
                .findFirst();
        if (active.isPresent()) {
            return InvestigationView.of(active.get());
        }
        if ("ESCALATED".equals(alert.status()) || "CLOSED".equals(alert.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Alert " + alertId + " is already " + alert.status());
        }

        // A model that is not local never sees customer data, so it cannot plan either.
        AgentProperties.Planner planner = properties.planner() == AgentProperties.Planner.LLM && noteWriter.modelIsLocal()
                ? AgentProperties.Planner.LLM
                : AgentProperties.Planner.FIXED;

        scoring.moveAlert(alertId, "IN_REVIEW");
        Investigation investigation = investigations.save(Investigation.start(alertId, alert.transactionId(),
                alert.customerId(), alert.amount(), alert.currency(), planner, clock.instant()));
        executor.execute(() -> runner.run(investigation.getId()));
        return InvestigationView.of(investigation);
    }

    @Transactional(readOnly = true)
    public InvestigationView get(UUID id) {
        return InvestigationView.of(find(id));
    }

    @Transactional(readOnly = true)
    public List<InvestigationView> forAlert(UUID alertId) {
        return investigations.findByAlertIdOrderByStartedAtDesc(alertId).stream().map(InvestigationView::of).toList();
    }

    @Transactional
    public InvestigationView analystDecides(UUID id, InvestigationDecision.Action action, String analyst, String comment) {
        Investigation investigation = find(id);
        investigation.analystDecides(action, analyst, comment, clock.instant());
        if (action == InvestigationDecision.Action.CLOSE) {
            scoring.moveAlert(investigation.getAlertId(), "CLOSED");
        }
        return InvestigationView.of(investigation);
    }

    @Transactional
    public InvestigationView approverDecides(UUID id, InvestigationDecision.Action action, String approver,
                                             String comment) {
        Investigation investigation = find(id);
        investigation.approverDecides(action, approver, comment, clock.instant());
        if (action == InvestigationDecision.Action.APPROVE) {
            scoring.moveAlert(investigation.getAlertId(), "ESCALATED");
        }
        return InvestigationView.of(investigation);
    }

    private Investigation find(UUID id) {
        return investigations.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No investigation " + id));
    }
}
