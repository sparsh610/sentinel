package com.sparsh.sentinel.ingest.transaction;

import com.sparsh.sentinel.ingest.transaction.TransactionEntity.Channel;
import com.sparsh.sentinel.ingest.transaction.TransactionEntity.Direction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** What the API returns about a transaction. Deliberately not the entity. */
public record TransactionView(
        UUID id,
        String externalRef,
        String customerId,
        Direction direction,
        Channel channel,
        BigDecimal amount,
        String currency,
        String counterpartyName,
        String counterpartyCountry,
        Instant bookedAt,
        Instant receivedAt
) {

    static TransactionView of(TransactionEntity entity) {
        return new TransactionView(
                entity.getId(),
                entity.getExternalRef(),
                entity.getCustomerId(),
                entity.getDirection(),
                entity.getChannel(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getCounterpartyName(),
                entity.getCounterpartyCountry(),
                entity.getBookedAt(),
                entity.getReceivedAt()
        );
    }
}
