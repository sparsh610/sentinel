package com.sparsh.sentinel.scoring.alert;

import com.sparsh.sentinel.scoring.detect.Finding;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** What the API returns about an alert. Deliberately not the entity. */
public record AlertView(
        UUID id,
        UUID transactionId,
        String customerId,
        String customerSegment,
        String direction,
        String channel,
        BigDecimal amount,
        String currency,
        String counterpartyName,
        String counterpartyCountry,
        Instant bookedAt,
        BigDecimal score,
        Alert.Status status,
        Instant raisedAt,
        List<FindingView> findings
) {

    public record FindingView(Finding.Rule rule, BigDecimal score, String reason) {
    }

    static AlertView of(Alert alert) {
        return new AlertView(
                alert.getId(),
                alert.getTransactionId(),
                alert.getCustomerId(),
                alert.getCustomerSegment(),
                alert.getDirection(),
                alert.getChannel(),
                alert.getAmount(),
                alert.getCurrency(),
                alert.getCounterpartyName(),
                alert.getCounterpartyCountry(),
                alert.getBookedAt(),
                alert.getScore(),
                alert.getStatus(),
                alert.getRaisedAt(),
                alert.getFindings().stream()
                        .map(f -> new FindingView(f.getRule(), f.getScore(), f.getReason()))
                        .toList()
        );
    }
}
