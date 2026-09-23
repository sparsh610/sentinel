package com.sparsh.sentinel.ingest.transaction;

import com.sparsh.sentinel.ingest.transaction.TransactionEntity.Channel;
import com.sparsh.sentinel.ingest.transaction.TransactionEntity.Direction;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A transaction as a source system submits it.
 *
 * @param externalRef the source system's reference. Resubmitting the same one is a no-op, which
 *                    is what makes it safe for a sender to retry.
 * @param currency    EUR only, for now. Every amount rule compares against a euro threshold,
 *                    and without FX conversion a 9,500 JPY payment would be judged as if it were
 *                    9,500 EUR. Rejecting it is better than scoring it wrongly.
 */
public record TransactionRequest(

        @NotBlank @Size(max = 64)
        String externalRef,

        @NotBlank @Size(max = 32)
        String customerId,

        @NotNull
        Direction direction,

        @NotNull
        Channel channel,

        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2)
        BigDecimal amount,

        @NotNull @Pattern(regexp = "EUR", message = "only EUR is supported until FX conversion exists")
        String currency,

        @Size(max = 256)
        String counterpartyName,

        @Pattern(regexp = "[A-Z]{2}", message = "must be an ISO 3166-1 alpha-2 code")
        String counterpartyCountry,

        @NotNull @PastOrPresent
        Instant bookedAt
) {
}
