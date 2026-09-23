package com.sparsh.sentinel.ingest.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparsh.sentinel.ingest.config.IngestProperties;
import com.sparsh.sentinel.ingest.customer.Customer;
import com.sparsh.sentinel.ingest.customer.CustomerRepository;
import com.sparsh.sentinel.ingest.outbox.OutboxEvent;
import com.sparsh.sentinel.ingest.outbox.OutboxRepository;
import com.sparsh.sentinel.ingest.outbox.TransactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Accepts a transaction: stores it and queues its event, in one database transaction.
 *
 * <p>This service does not talk to Kafka. It writes an outbox row next to the transaction and
 * {@link com.sparsh.sentinel.ingest.outbox.OutboxRelay} publishes it. Either both rows commit
 * or neither does, so a stored transaction is never left unscored and an event never describes
 * a transaction that was rolled back.
 */
@Service
public class TransactionIngestService {

    private static final Logger log = LoggerFactory.getLogger(TransactionIngestService.class);

    private final TransactionRepository transactions;
    private final CustomerRepository customers;
    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final IngestProperties properties;
    private final Clock clock;

    public TransactionIngestService(TransactionRepository transactions,
                                    CustomerRepository customers,
                                    OutboxRepository outbox,
                                    ObjectMapper objectMapper,
                                    IngestProperties properties,
                                    Clock clock) {
        this.transactions = transactions;
        this.customers = customers;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @return the stored transaction, and whether this call created it. A repeated
     *         {@code externalRef} returns the original untouched and queues nothing.
     * @throws UnknownCustomerException if the customer does not exist
     */
    @Transactional
    public IngestResult ingest(TransactionRequest request) {
        Optional<TransactionEntity> existing = transactions.findByExternalRef(request.externalRef());
        if (existing.isPresent()) {
            log.debug("Replay of {} ignored", request.externalRef());
            return new IngestResult(TransactionView.of(existing.get()), false);
        }

        Customer customer = customers.findById(request.customerId())
                .orElseThrow(() -> new UnknownCustomerException(request.customerId()));

        Instant now = clock.instant();

        TransactionEntity tx = transactions.save(new TransactionEntity(
                UUID.randomUUID(),
                request.externalRef(),
                customer.getId(),
                request.direction(),
                request.channel(),
                request.amount(),
                request.currency(),
                request.counterpartyName(),
                request.counterpartyCountry(),
                request.bookedAt(),
                now
        ));

        // Keyed by customer: Kafka orders messages only within a partition, and every rule that
        // looks at a customer's recent history needs that customer's transactions in order.
        outbox.save(new OutboxEvent(
                tx.getId(),
                properties.topic(),
                customer.getId(),
                toJson(TransactionEvent.of(tx, customer)),
                now
        ));

        return new IngestResult(TransactionView.of(tx), true);
    }

    @Transactional(readOnly = true)
    public List<TransactionView> recentForCustomer(String customerId, int limit) {
        return transactions.findByCustomerIdOrderByBookedAtDesc(customerId, PageRequest.of(0, limit))
                .stream()
                .map(TransactionView::of)
                .toList();
    }

    private String toJson(TransactionEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // A record of strings, numbers and an Instant always serialises; if it does not,
            // that is a programming error and the transaction must roll back with it.
            throw new IllegalStateException("Could not serialise " + event.transactionId(), e);
        }
    }

    public record IngestResult(TransactionView transaction, boolean created) {
    }
}
