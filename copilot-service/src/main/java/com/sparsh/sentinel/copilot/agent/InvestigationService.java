package com.sparsh.sentinel.copilot.agent;

import com.sparsh.sentinel.copilot.agent.client.ScoringClient;
import com.sparsh.sentinel.copilot.agent.client.ScoringClient.AlertSnapshot;
import com.sparsh.sentinel.copilot.audit.AuditEvent.Channel;
import com.sparsh.sentinel.copilot.audit.AuditEvent.Outcome;
import com.sparsh.sentinel.copilot.audit.AuditLog;
import com.sparsh.sentinel.copilot.security.CurrentUser;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
 *
 * <p>Who is acting comes from the Keycloak token, never from the request body, and the role
 * checks sit on these methods rather than only on the URLs: the MCP endpoint reaches
 * {@link #start} without passing the REST controller, and gets the same rule.
 */
@Service
public class InvestigationService {

    private final InvestigationRepository investigations;
    private final ScoringClient scoring;
    private final InvestigationRunner runner;
    private final TaskExecutor executor;
    private final CaseNoteWriter noteWriter;
    private final AgentProperties properties;
    private final AuditLog audit;
    private final Clock clock;

    public InvestigationService(InvestigationRepository investigations, ScoringClient scoring,
                                InvestigationRunner runner, TaskExecutor executor, CaseNoteWriter noteWriter,
                                AgentProperties properties, AuditLog audit, Clock clock) {
        this.investigations = investigations;
        this.scoring = scoring;
        this.runner = runner;
        this.executor = executor;
        this.noteWriter = noteWriter;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Starts an investigation and returns at once; the agent runs in the background. An alert
     * that already has one in progress or awaiting a decision gets that one back, not a second.
     *
     * <p>The run is recorded against the person who started it, and its policy lookup is held to
     * what that person may see - the agent has no identity of its own to widen it.
     */
    @PreAuthorize("hasAnyRole('ANALYST', 'SENIOR_APPROVER')")
    public InvestigationView start(UUID alertId) {
        return start(alertId, Channel.API);
    }

    /** The same, saying which door it came through - the console, or an MCP client. */
    @PreAuthorize("hasAnyRole('ANALYST', 'SENIOR_APPROVER')")
    public InvestigationView start(UUID alertId, Channel channel) {
        CurrentUser user = CurrentUser.get();

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
                alert.customerId(), alert.amount(), alert.currency(), planner, user.name(), user.clearance(),
                clock.instant()));
        audit.record(channel, "INVESTIGATION_STARTED", alertId.toString(), Outcome.OK,
                "investigation " + investigation.getId() + ", " + planner + " plan, policy clearance "
                        + investigation.getClearance());
        executor.execute(() -> runner.run(investigation.getId()));
        return InvestigationView.of(investigation);
    }

    @PreAuthorize("hasAnyRole('ANALYST', 'SENIOR_APPROVER')")
    @Transactional(readOnly = true)
    public InvestigationView get(UUID id) {
        return InvestigationView.of(find(id));
    }

    @PreAuthorize("hasAnyRole('ANALYST', 'SENIOR_APPROVER')")
    @Transactional(readOnly = true)
    public List<InvestigationView> forAlert(UUID alertId) {
        return investigations.findByAlertIdOrderByStartedAtDesc(alertId).stream().map(InvestigationView::of).toList();
    }

    /** The analyst's decision, in the name on their token. */
    @PreAuthorize("hasRole('ANALYST')")
    @Transactional
    public InvestigationView analystDecides(UUID id, InvestigationDecision.Action action, String comment) {
        String analyst = CurrentUser.get().name();
        Investigation investigation = find(id);
        try {
            investigation.analystDecides(action, analyst, comment, clock.instant());
        } catch (IllegalStateException | IllegalArgumentException e) {
            audit.refused(Channel.API, "DECISION", id.toString(), Outcome.DENIED, action + ": " + e.getMessage());
            throw e;
        }
        audit.record(Channel.API, "DECISION", id.toString(), Outcome.OK, action + described(comment));
        if (action == InvestigationDecision.Action.CLOSE) {
            scoring.moveAlert(investigation.getAlertId(), "CLOSED");
        }
        return InvestigationView.of(investigation);
    }

    /**
     * The senior approver's sign-off, in the name on their token. Four eyes is checked against
     * that name, so it can no longer be met by typing a different one.
     */
    @PreAuthorize("hasRole('SENIOR_APPROVER')")
    @Transactional
    public InvestigationView approverDecides(UUID id, InvestigationDecision.Action action, String comment) {
        String approver = CurrentUser.get().name();
        Investigation investigation = find(id);
        try {
            investigation.approverDecides(action, approver, comment, clock.instant());
        } catch (IllegalStateException | IllegalArgumentException e) {
            // Includes the four-eyes refusal: an attempt to sign off one's own escalation is kept.
            audit.refused(Channel.API, "SIGN_OFF", id.toString(), Outcome.DENIED, action + ": " + e.getMessage());
            throw e;
        }
        audit.record(Channel.API, "SIGN_OFF", id.toString(), Outcome.OK, action + described(comment));
        if (action == InvestigationDecision.Action.APPROVE) {
            scoring.moveAlert(investigation.getAlertId(), "ESCALATED");
        }
        return InvestigationView.of(investigation);
    }

    private static String described(String comment) {
        return comment == null || comment.isBlank() ? "" : " - " + comment.strip();
    }

    private Investigation find(UUID id) {
        return investigations.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No investigation " + id));
    }
}
