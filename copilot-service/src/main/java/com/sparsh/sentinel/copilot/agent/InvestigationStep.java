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

/** One tool call in an investigation's execution trace. */
@Entity
@Table(name = "investigation_step", schema = "sentinel")
public class InvestigationStep {

    public enum Status {
        OK,
        ERROR,
        /** Not run: the step limit had been reached. */
        REFUSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investigation_id")
    private Investigation investigation;

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @Column(nullable = false, length = 32)
    private String tool;

    @Column(nullable = false)
    private String input;

    private String output;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Status status;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    protected InvestigationStep() {
        // for JPA
    }

    InvestigationStep(Investigation investigation, int stepNo, String tool, String input, String output,
                      Status status, int durationMs, Instant startedAt) {
        this.investigation = investigation;
        this.stepNo = stepNo;
        this.tool = tool;
        this.input = input;
        this.output = output;
        this.status = status;
        this.durationMs = durationMs;
        this.startedAt = startedAt;
    }

    public int getStepNo() {
        return stepNo;
    }

    public String getTool() {
        return tool;
    }

    public String getInput() {
        return input;
    }

    public String getOutput() {
        return output;
    }

    public Status getStatus() {
        return status;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public Instant getStartedAt() {
        return startedAt;
    }
}
