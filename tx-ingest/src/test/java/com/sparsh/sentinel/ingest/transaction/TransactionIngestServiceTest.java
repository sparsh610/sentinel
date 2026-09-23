package com.sparsh.sentinel.ingest.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparsh.sentinel.ingest.config.IngestProperties;
import com.sparsh.sentinel.ingest.customer.Customer;
import com.sparsh.sentinel.ingest.customer.Customer.RiskRating;
import com.sparsh.sentinel.ingest.customer.Customer.Segment;
import com.sparsh.sentinel.ingest.customer.CustomerRepository;
import com.sparsh.sentinel.ingest.outbox.OutboxEvent;
import com.sparsh.sentinel.ingest.outbox.OutboxRepository;
import com.sparsh.sentinel.ingest.transaction.TransactionEntity.Channel;
import com.sparsh.sentinel.ingest.transaction.TransactionEntity.Direction;
import com.sparsh.sentinel.ingest.transaction.TransactionIngestService.IngestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the two promises ingestion makes: every stored transaction gets exactly one queued
 * event, and a replayed {@code externalRef} gets none.
 */
@ExtendWith(MockitoExtension.class)
class TransactionIngestServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");

    private static final Customer CUSTOMER = new Customer(
            "C-10001", "Anna Weber", Segment.RETAIL, "DE", RiskRating.LOW, LocalDate.of(2016, 3, 14));

    @Mock
    private TransactionRepository transactions;

    @Mock
    private CustomerRepository customers;

    @Mock
    private OutboxRepository outbox;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private TransactionIngestService service;

    @BeforeEach
    void setUp() {
        IngestProperties properties = new IngestProperties(
                "transactions", 3, new IngestProperties.Outbox(false, 500, 100));
        service = new TransactionIngestService(transactions, customers, outbox, objectMapper,
                properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void storesTheTransactionAndQueuesOneEventKeyedByCustomer() throws Exception {
        when(transactions.findByExternalRef("REF-1")).thenReturn(Optional.empty());
        when(customers.findById("C-10001")).thenReturn(Optional.of(CUSTOMER));
        when(transactions.save(any())).thenAnswer(call -> call.getArgument(0));

        IngestResult result = service.ingest(request("REF-1"));

        assertThat(result.created()).isTrue();
        assertThat(result.transaction().receivedAt()).isEqualTo(NOW);

        ArgumentCaptor<OutboxEvent> queued = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(queued.capture());

        OutboxEvent event = queued.getValue();
        assertThat(event.getTopic()).isEqualTo("transactions");
        assertThat(event.getMessageKey()).isEqualTo("C-10001");
        assertThat(event.getAggregateId()).isEqualTo(result.transaction().id());
        assertThat(event.getPublishedAt()).isNull();

        JsonNode payload = objectMapper.readTree(event.getPayload());
        assertThat(payload.get("transactionId").asText()).isEqualTo(result.transaction().id().toString());
        assertThat(payload.get("customerSegment").asText()).isEqualTo("RETAIL");
        assertThat(payload.get("amount").decimalValue()).isEqualByComparingTo("9500.00");
        assertThat(payload.get("schemaVersion").asInt()).isEqualTo(1);
    }

    @Test
    void aReplayedReferenceReturnsTheOriginalAndQueuesNothing() {
        TransactionEntity original = new TransactionEntity(UUID.randomUUID(), "REF-1", "C-10001",
                Direction.CREDIT, Channel.CASH, new BigDecimal("9500.00"), "EUR", null, null,
                NOW.minusSeconds(3600), NOW.minusSeconds(60));
        when(transactions.findByExternalRef("REF-1")).thenReturn(Optional.of(original));

        IngestResult result = service.ingest(request("REF-1"));

        assertThat(result.created()).isFalse();
        assertThat(result.transaction().id()).isEqualTo(original.getId());
        verify(transactions, never()).save(any());
        verify(outbox, never()).save(any());
    }

    @Test
    void anUnknownCustomerIsRejectedBeforeAnythingIsWritten() {
        when(transactions.findByExternalRef("REF-1")).thenReturn(Optional.empty());
        when(customers.findById("C-10001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ingest(request("REF-1")))
                .isInstanceOf(UnknownCustomerException.class);

        verify(transactions, never()).save(any());
        verify(outbox, never()).save(any());
    }

    private static TransactionRequest request(String externalRef) {
        return new TransactionRequest(externalRef, "C-10001", Direction.CREDIT, Channel.CASH,
                new BigDecimal("9500.00"), "EUR", null, null, NOW.minusSeconds(3600));
    }
}
