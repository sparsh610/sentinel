package com.sparsh.sentinel.scoring.score;

import com.sparsh.sentinel.scoring.event.TransactionEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A transaction this service has already scored. Its primary key is the idempotency guard, and
 * the rows are the history the detectors look back over.
 */
@Entity
@Table(name = "scored_transaction", schema = "sentinel")
public class ScoredTransaction {

    @Id
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "customer_id", nullable = false, length = 32)
    private String customerId;

    @Column(nullable = false, length = 8)
    private String direction;

    @Column(nullable = false, length = 16)
    private String channel;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "counterparty_country", length = 2)
    private String counterpartyCountry;

    @Column(name = "counterparty_name", length = 256)
    private String counterpartyName;

    @Column(name = "booked_at", nullable = false)
    private Instant bookedAt;

    @Column(name = "scored_at", nullable = false)
    private Instant scoredAt;

    protected ScoredTransaction() {
        // for JPA
    }

    public static ScoredTransaction of(TransactionEvent event, Instant scoredAt) {
        ScoredTransaction tx = new ScoredTransaction();
        tx.transactionId = event.transactionId();
        tx.customerId = event.customerId();
        tx.direction = event.direction();
        tx.channel = event.channel();
        tx.amount = event.amount();
        tx.currency = event.currency();
        tx.counterpartyCountry = event.counterpartyCountry();
        tx.counterpartyName = event.counterpartyName();
        tx.bookedAt = event.bookedAt();
        tx.scoredAt = scoredAt;
        return tx;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getCustomerId() {
        return customerId;
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

    public String getCounterpartyCountry() {
        return counterpartyCountry;
    }

    public String getCounterpartyName() {
        return counterpartyName;
    }

    public Instant getBookedAt() {
        return bookedAt;
    }

    public Instant getScoredAt() {
        return scoredAt;
    }
}
