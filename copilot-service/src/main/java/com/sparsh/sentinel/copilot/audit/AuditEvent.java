package com.sparsh.sentinel.copilot.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One line of the audit log. Inserted, never changed - the table's triggers refuse it. */
@Entity
@Table(name = "audit_event", schema = "sentinel")
public class AuditEvent {

    /** Which door the action came through. */
    public enum Channel {
        /** The console, or any other REST caller. */
        API,
        /** An MCP client - Claude Code, Codex CLI - calling the published tools. */
        MCP,
        /** The investigation agent, working for the person who started the run. */
        AGENT
    }

    public enum Outcome { OK, DENIED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 128)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Channel channel;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(length = 128)
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Outcome outcome;

    private String detail;

    protected AuditEvent() {
        // for JPA
    }

    AuditEvent(Instant occurredAt, String actor, Channel channel, String action, String target, Outcome outcome,
               String detail) {
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.channel = channel;
        this.action = action;
        this.target = target;
        this.outcome = outcome;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActor() {
        return actor;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getDetail() {
        return detail;
    }
}
