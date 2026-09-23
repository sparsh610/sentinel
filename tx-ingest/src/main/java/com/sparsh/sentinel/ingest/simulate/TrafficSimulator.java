package com.sparsh.sentinel.ingest.simulate;

import com.sparsh.sentinel.ingest.customer.Customer;
import com.sparsh.sentinel.ingest.customer.CustomerRepository;
import com.sparsh.sentinel.ingest.transaction.TransactionEntity.Channel;
import com.sparsh.sentinel.ingest.transaction.TransactionEntity.Direction;
import com.sparsh.sentinel.ingest.transaction.TransactionIngestService;
import com.sparsh.sentinel.ingest.transaction.TransactionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Generates synthetic traffic for the demo: a batch of ordinary transactions with three known
 * typologies planted in it - a structuring burst, a large cash deposit and a payment to a
 * high-risk jurisdiction.
 *
 * <p>Everything goes through {@link TransactionIngestService}, the same path as the REST API, so
 * a simulation exercises the outbox, Kafka and scoring exactly as real traffic would.
 *
 * <p>The planted cases are there so the alert queue has something true to show. They are not a
 * test of the detectors - those have unit tests - and a queue that lights up only on what was
 * planted says nothing about false positives. Measuring those is the week-8 evaluation page.
 */
@Component
public class TrafficSimulator {

    private static final List<String> COUNTERPARTIES = List.of(
            "REWE Markt", "Deutsche Bahn", "Stadtwerke Leipzig", "Amazon EU", "Lidl",
            "Allianz Versicherung", "Vodafone GmbH", "IKEA Deutschland", "Shell Station", "Apotheke am Markt");

    private static final List<String> ORDINARY_COUNTRIES = List.of("DE", "DE", "DE", "AT", "NL", "FR", "IT", "PL");

    private final TransactionIngestService ingestService;
    private final CustomerRepository customers;
    private final Clock clock;
    private final RandomGenerator random;

    @Autowired
    public TrafficSimulator(TransactionIngestService ingestService,
                            CustomerRepository customers,
                            Clock clock) {
        this(ingestService, customers, clock, RandomGenerator.getDefault());
    }

    TrafficSimulator(TransactionIngestService ingestService,
                     CustomerRepository customers,
                     Clock clock,
                     RandomGenerator random) {
        this.ingestService = ingestService;
        this.customers = customers;
        this.clock = clock;
        this.random = random;
    }

    public SimulationReport run(int ordinaryCount) {
        List<Customer> all = customers.findAll();
        if (all.isEmpty()) {
            throw new IllegalStateException(
                    "No customers to simulate against. Is db/demo in spring.flyway.locations?");
        }

        Instant now = clock.instant();
        List<TransactionRequest> batch = new ArrayList<>();
        List<String> planted = new ArrayList<>();

        for (int i = 0; i < ordinaryCount; i++) {
            batch.add(ordinary(pick(all), now.minus(Duration.ofMinutes(random.nextInt(6 * 60)))));
        }

        Customer structurer = pick(all);
        Instant start = now.minus(Duration.ofHours(3));
        for (int i = 0; i < 3; i++) {
            // Just under the EUR 10,000 cash threshold, within a few hours: the textbook pattern.
            batch.add(request(structurer, Direction.CREDIT, Channel.CASH,
                    amountBetween(9_200, 9_950), null, null, start.plus(Duration.ofMinutes(50L * i))));
        }
        planted.add("Structuring: 3 cash deposits just under EUR 10,000 for " + structurer.getId());

        Customer depositor = pick(all);
        batch.add(request(depositor, Direction.CREDIT, Channel.CASH,
                amountBetween(12_000, 25_000), null, null, now.minus(Duration.ofMinutes(20))));
        planted.add("Large cash deposit for " + depositor.getId());

        Customer payer = pick(all);
        batch.add(request(payer, Direction.DEBIT, Channel.TRANSFER,
                amountBetween(4_000, 30_000), "Kish Trading Co.", "IR", now.minus(Duration.ofMinutes(10))));
        planted.add("Transfer to a high-risk jurisdiction (IR) for " + payer.getId());

        // Submitted in booking order, the way a real feed arrives. The history rules in scoring
        // look back from each transaction, so feeding them out of order would hide a pattern.
        batch.sort(Comparator.comparing(TransactionRequest::bookedAt));
        batch.forEach(ingestService::ingest);

        return new SimulationReport(batch.size(), planted);
    }

    private TransactionRequest ordinary(Customer customer, Instant bookedAt) {
        return switch (customer.getSegment()) {
            case RETAIL -> random.nextInt(10) < 8
                    ? request(customer, Direction.DEBIT, Channel.CARD, amountBetween(4, 180),
                            pick(COUNTERPARTIES), "DE", bookedAt)
                    : request(customer, Direction.DEBIT, Channel.TRANSFER, amountBetween(50, 1_400),
                            pick(COUNTERPARTIES), pick(ORDINARY_COUNTRIES), bookedAt);
            case BUSINESS -> random.nextBoolean()
                    ? request(customer, Direction.CREDIT, Channel.CASH, amountBetween(200, 2_500),
                            null, null, bookedAt)
                    : request(customer, Direction.DEBIT, Channel.TRANSFER, amountBetween(300, 8_000),
                            pick(COUNTERPARTIES), pick(ORDINARY_COUNTRIES), bookedAt);
            case CORPORATE -> request(customer, random.nextBoolean() ? Direction.DEBIT : Direction.CREDIT,
                    Channel.TRANSFER, amountBetween(5_000, 250_000), pick(COUNTERPARTIES),
                    pick(ORDINARY_COUNTRIES), bookedAt);
        };
    }

    private static TransactionRequest request(Customer customer, Direction direction, Channel channel,
                                              BigDecimal amount, String counterparty, String country,
                                              Instant bookedAt) {
        return new TransactionRequest(
                "SIM-" + UUID.randomUUID(),
                customer.getId(),
                direction,
                channel,
                amount,
                "EUR",
                counterparty,
                country,
                bookedAt
        );
    }

    private BigDecimal amountBetween(int min, int max) {
        double value = min + random.nextDouble() * (max - min);
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private <T> T pick(List<T> items) {
        return items.get(random.nextInt(items.size()));
    }

    public record SimulationReport(int submitted, List<String> planted) {
    }
}
