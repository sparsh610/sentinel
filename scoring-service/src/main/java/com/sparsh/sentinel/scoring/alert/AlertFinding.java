package com.sparsh.sentinel.scoring.alert;

import com.sparsh.sentinel.scoring.detect.Finding;
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

import java.math.BigDecimal;

@Entity
@Table(name = "alert_finding", schema = "sentinel")
public class AlertFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_id", nullable = false)
    private Alert alert;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Finding.Rule rule;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal score;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    protected AlertFinding() {
        // for JPA
    }

    AlertFinding(Alert alert, Finding finding) {
        this.alert = alert;
        this.rule = finding.rule();
        this.score = finding.score();
        this.reason = finding.reason();
    }

    public Finding.Rule getRule() {
        return rule;
    }

    public BigDecimal getScore() {
        return score;
    }

    public String getReason() {
        return reason;
    }
}
