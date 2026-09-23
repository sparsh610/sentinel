package com.sparsh.sentinel.ingest.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction", schema = "sentinel")
public class TransactionEntity {

    public enum Direction { DEBIT, CREDIT }

    public enum Channel { CARD, TRANSFER, CASH }

    @Id
    private UUID id;

    @Column(name = "external_ref", nullable = false, length = 64)
    private String externalRef;

    @Column(name = "customer_id", nullable = false, length = 32)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Channel channel;

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

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected TransactionEntity() {
        // for JPA
    }

    public TransactionEntity(UUID id, String externalRef, String customerId, Direction direction,
                             Channel channel, BigDecimal amount, String currency,
                             String counterpartyName, String counterpartyCountry,
                             Instant bookedAt, Instant receivedAt) {
        this.id = id;
        this.externalRef = externalRef;
        this.customerId = customerId;
        this.direction = direction;
        this.channel = channel;
        this.amount = amount;
        this.currency = currency;
        this.counterpartyName = counterpartyName;
        this.counterpartyCountry = counterpartyCountry;
        this.bookedAt = bookedAt;
        this.receivedAt = receivedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public String getCustomerId() {
        return customerId;
    }

    public Direction getDirection() {
        return direction;
    }

    public Channel getChannel() {
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

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
