package com.sparsh.sentinel.copilot.agent;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The agent's work on one alert, and where the people deciding it have got to.
 *
 * <p>The lifecycle encodes the policy the agent cites (AML-05.2): an analyst may close a case or
 * escalate it, but an escalation becomes a report only when a senior approver - a different
 * person - signs it off. The agent itself never moves a case past DRAFTED.
 */
@Entity
@Table(name = "investigation", schema = "sentinel")
public class Investigation {

    public enum Status { RUNNING, FAILED, DRAFTED, AWAITING_SIGN_OFF, REPORTED, CLOSED }

    public enum NoteSource { MODEL, TEMPLATE }

    @Id
    private UUID id;

    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "customer_id", nullable = false, length = 32)
    private String customerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private AgentProperties.Planner planner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "case_note")
    private String caseNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_source", length = 8)
    private NoteSource noteSource;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @OneToMany(mappedBy = "investigation")
    @OrderBy("stepNo")
    private List<InvestigationStep> steps = new ArrayList<>();

    @OneToMany(mappedBy = "investigation", cascade = CascadeType.PERSIST)
    @OrderBy("decidedAt")
    private List<InvestigationDecision> decisions = new ArrayList<>();

    protected Investigation() {
        // for JPA
    }

    public static Investigation start(UUID alertId, UUID transactionId, String customerId, BigDecimal amount,
                                      String currency, AgentProperties.Planner planner, Instant now) {
        Investigation investigation = new Investigation();
        investigation.id = UUID.randomUUID();
        investigation.alertId = alertId;
        investigation.transactionId = transactionId;
        investigation.customerId = customerId;
        investigation.amount = amount;
        investigation.currency = currency;
        investigation.planner = planner;
        investigation.status = Status.RUNNING;
        investigation.startedAt = now;
        return investigation;
    }

    /** Still in progress or waiting for a person - not something to start a second run beside. */
    public boolean isActive() {
        return status == Status.RUNNING || status == Status.DRAFTED || status == Status.AWAITING_SIGN_OFF;
    }

    void drafted(String note, NoteSource source, Instant now) {
        require(Status.RUNNING, "draft a note");
        caseNote = note;
        noteSource = source;
        status = Status.DRAFTED;
        finishedAt = now;
    }

    void failed(String reason, Instant now) {
        require(Status.RUNNING, "fail");
        failureReason = reason;
        status = Status.FAILED;
        finishedAt = now;
    }

    /** The analyst's call on the draft: escalate it for sign-off, or close it. */
    void analystDecides(InvestigationDecision.Action action, String analyst, String comment, Instant now) {
        require(Status.DRAFTED, "take an analyst decision");
        status = switch (action) {
            case ESCALATE -> Status.AWAITING_SIGN_OFF;
            case CLOSE -> Status.CLOSED;
            default -> throw new IllegalArgumentException("An analyst can ESCALATE or CLOSE, not " + action);
        };
        decisions.add(new InvestigationDecision(this, analyst.strip(), InvestigationDecision.Role.ANALYST,
                action, comment, now));
    }

    /**
     * The senior approver's call on an escalation: report it, or return it to the analyst.
     * Four eyes: the person who escalated cannot be the one who signs it off.
     */
    void approverDecides(InvestigationDecision.Action action, String approver, String comment, Instant now) {
        require(Status.AWAITING_SIGN_OFF, "sign off");
        String escalatedBy = decisions.stream()
                .filter(d -> d.getAction() == InvestigationDecision.Action.ESCALATE)
                .reduce((first, second) -> second)
                .map(InvestigationDecision::getActor)
                .orElse(null);
        if (approver.strip().equalsIgnoreCase(escalatedBy)) {
            throw new IllegalStateException(approver.strip() + " escalated this case and cannot also sign it off");
        }
        status = switch (action) {
            case APPROVE -> Status.REPORTED;
            case RETURN -> Status.DRAFTED;
            default -> throw new IllegalArgumentException("A senior approver can APPROVE or RETURN, not " + action);
        };
        decisions.add(new InvestigationDecision(this, approver.strip(), InvestigationDecision.Role.SENIOR_APPROVER,
                action, comment, now));
    }

    private void require(Status expected, String what) {
        if (status != expected) {
            throw new IllegalStateException("Investigation " + id + " is " + status + "; cannot " + what);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public AgentProperties.Planner getPlanner() {
        return planner;
    }

    public Status getStatus() {
        return status;
    }

    public String getCaseNote() {
        return caseNote;
    }

    public NoteSource getNoteSource() {
        return noteSource;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public List<InvestigationStep> getSteps() {
        return steps;
    }

    public List<InvestigationDecision> getDecisions() {
        return decisions;
    }
}
