package com.sparsh.sentinel.copilot.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** A person's decision on an investigation. Appended, never changed. */
@Entity
@Table(name = "investigation_decision", schema = "sentinel")
public class InvestigationDecision {

    public enum Role { ANALYST, SENIOR_APPROVER }

    public enum Action {
        /** Analyst: suspicious - send to a senior approver to report. */
        ESCALATE,
        /** Analyst: no further action. */
        CLOSE,
        /** Senior approver: report it. */
        APPROVE,
        /** Senior approver: back to the analyst. */
        RETURN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investigation_id")
    private Investigation investigation;

    @Column(nullable = false, length = 64)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Action action;

    private String comment;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected InvestigationDecision() {
        // for JPA
    }

    InvestigationDecision(Investigation investigation, String actor, Role role, Action action,
                          String comment, Instant decidedAt) {
        this.investigation = investigation;
        this.actor = actor;
        this.role = role;
        this.action = action;
        this.comment = comment;
        this.decidedAt = decidedAt;
    }

    public String getActor() {
        return actor;
    }

    public Role getRole() {
        return role;
    }

    public Action getAction() {
        return action;
    }

    public String getComment() {
        return comment;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
