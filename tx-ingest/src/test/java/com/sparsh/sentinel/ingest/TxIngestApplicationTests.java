package com.sparsh.sentinel.ingest;

import com.sparsh.sentinel.ingest.customer.CustomerRepository;
import com.sparsh.sentinel.ingest.outbox.OutboxRelay;
import com.sparsh.sentinel.ingest.outbox.OutboxRepository;
import com.sparsh.sentinel.ingest.transaction.TransactionIngestService;
import com.sparsh.sentinel.ingest.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context smoke test.
 *
 * <p>Postgres and Kafka are stubbed out: this asserts the application's own wiring, not that the
 * infrastructure works. The round trip through a real database and broker is a Testcontainers
 * test, scheduled for week 8.
 */
@SpringBootTest
class TxIngestApplicationTests {

    @MockitoBean
    private TransactionRepository transactionRepository;

    @MockitoBean
    private CustomerRepository customerRepository;

    @MockitoBean
    private OutboxRepository outboxRepository;

    @Autowired
    private TransactionIngestService ingestService;

    @Autowired
    private OutboxRelay relay;

    @Test
    void contextLoads() {
        assertThat(ingestService).isNotNull();
        assertThat(relay).isNotNull();
    }
}
