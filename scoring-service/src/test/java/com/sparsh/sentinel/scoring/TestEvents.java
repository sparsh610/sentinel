package com.sparsh.sentinel.scoring;

import com.sparsh.sentinel.scoring.config.ScoringProperties;
import com.sparsh.sentinel.scoring.event.TransactionEvent;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Builders shared by the scoring tests. */
public final class TestEvents {

    public static final Instant BOOKED = Instant.parse("2026-09-23T10:00:00Z");

    public static final ScoringProperties PROPERTIES = new ScoringProperties(
            "transactions", "transactions-dlt", 3,
            new BigDecimal("10000"),
            new ScoringProperties.Structuring(new BigDecimal("9000"), 3, Duration.ofHours(24)),
            Set.of("KP", "IR", "MM"));

    private TestEvents() {
    }

    public static TransactionEvent cashDeposit(String amount) {
        return event("CREDIT", "CASH", amount, null);
    }

    public static TransactionEvent event(String direction, String channel, String amount, String country) {
        return new TransactionEvent(1, UUID.randomUUID(), "REF", "C-10001", "RETAIL", "LOW",
                direction, channel, new BigDecimal(amount), "EUR", null, country, BOOKED);
    }
}
