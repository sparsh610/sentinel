package com.sparsh.sentinel.scoring.alert;

import com.sparsh.sentinel.scoring.detect.Finding;
import com.sparsh.sentinel.scoring.event.TransactionEvent;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A transaction that needs a human to look at it, with every finding against it.
 *
 * <p>The transaction's details are a snapshot taken when the alert was raised, not a reference:
 * an alert has to keep showing the facts it fired on, even if the source is corrected later.
 */
@Entity
@Table(name = "alert", schema = "sentinel")
public class Alert {

    public enum Status { OPEN, IN_REVIEW, ESCALATED, CLOSED }

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "customer_id", nullable = false, length = 32)
    private String customerId;

    @Column(name = "customer_segment", nullable = false, length = 16)
    private String customerSegment;

    @Column(nullable = false, length = 8)
    private String direction;

    @Column(nullable = false, length = 16)
    private String channel;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "counterparty_name", length = 256)
    private String counterpartyName;

    @Column(name = "counterparty_country", length = 2)
    private String counterpartyCountry;

    @Column(name = "booked_at", nullable = false)
    private Instant bookedAt;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    @OneToMany(mappedBy = "alert", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("score DESC")
    private List<AlertFinding> findings = new ArrayList<>();

    protected Alert() {
        // for JPA
    }

    /**
     * @param findings at least one; an alert with no reason is not an alert.
     */
    public static Alert raise(TransactionEvent tx, List<Finding> findings, Instant now) {
        if (findings.isEmpty()) {
            throw new IllegalArgumentException("An alert needs at least one finding");
        }

        Alert alert = new Alert();
        alert.id = UUID.randomUUID();
        alert.transactionId = tx.transactionId();
        alert.customerId = tx.customerId();
        alert.customerSegment = tx.customerSegment();
        alert.direction = tx.direction();
        alert.channel = tx.channel();
        alert.amount = tx.amount();
        alert.currency = tx.currency();
        alert.counterpartyName = tx.counterpartyName();
        alert.counterpartyCountry = tx.counterpartyCountry();
        alert.bookedAt = tx.bookedAt();
        alert.status = Status.OPEN;
        alert.raisedAt = now;
        alert.score = findings.stream().map(Finding::score).max(Comparator.naturalOrder()).orElseThrow();
        findings.forEach(f -> alert.findings.add(new AlertFinding(alert, f)));
        return alert;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getCustomerSegment() {
        return customerSegment;
    }

    public String getDirection() {
        return direction;
    }

    public String getChannel() {
        return channel;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCounterpartyName() {
        return counterpartyName;
    }

    public String getCounterpartyCountry() {
        return counterpartyCountry;
    }

    public Instant getBookedAt() {
        return bookedAt;
    }

    public BigDecimal getScore() {
        return score;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    public List<AlertFinding> getFindings() {
        return List.copyOf(findings);
    }
}
