package com.sparsh.sentinel.scoring.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A transaction as it arrives on the {@code transactions} topic.
 *
 * <p>scoring-service's own copy of the contract, not a class shared with tx-ingest - the JSON is
 * the contract, so the two services release independently. Fields this record does not declare
 * are ignored, which is what lets the producer add fields without breaking this consumer.
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

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public boolean isCash() {
        return "CASH".equals(channel);
    }

    public boolean isCredit() {
        return "CREDIT".equals(direction);
    }
}
