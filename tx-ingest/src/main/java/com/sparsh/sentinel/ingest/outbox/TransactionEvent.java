package com.sparsh.sentinel.ingest.outbox;

import com.sparsh.sentinel.ingest.customer.Customer;
import com.sparsh.sentinel.ingest.transaction.TransactionEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The message on the {@code transactions} topic - the whole contract between ingest and scoring.
 *
 * <p>It carries the customer context scoring needs, so that scoring never reads this service's
 * tables. There is no shared library holding this record: scoring-service declares its own copy,
 * and the JSON is the contract. A shared jar would force both services to release together, which
 * is the coupling the broker exists to remove.
 *
 * <p>Add fields freely; consumers ignore what they do not know. Renaming or removing one is a
 * breaking change and needs a new topic version.
 */
public record TransactionEvent(
        int schemaVersion,
        UUID transactionId,
        String externalRef,
        String customerId,
        String customerSegment,
        String customerRiskRating,
        String direction,
        String channel,
        BigDecimal amount,
        String currency,
        String counterpartyName,
        String counterpartyCountry,
        Instant bookedAt
) {

    public static final int SCHEMA_VERSION = 1;

    public static TransactionEvent of(TransactionEntity tx, Customer customer) {
        return new TransactionEvent(
                SCHEMA_VERSION,
                tx.getId(),
                tx.getExternalRef(),
                tx.getCustomerId(),
                customer.getSegment().name(),
                customer.getRiskRating().name(),
                tx.getDirection().name(),
                tx.getChannel().name(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getCounterpartyName(),
                tx.getCounterpartyCountry(),
                tx.getBookedAt()
        );
    }
}
