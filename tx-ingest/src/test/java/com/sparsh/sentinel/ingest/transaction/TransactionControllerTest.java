package com.sparsh.sentinel.ingest.transaction;

import com.sparsh.sentinel.ingest.transaction.TransactionEntity.Channel;
import com.sparsh.sentinel.ingest.transaction.TransactionEntity.Direction;
import com.sparsh.sentinel.ingest.transaction.TransactionIngestService.IngestResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    private static final String VALID = """
            {"externalRef":"REF-1","customerId":"C-10001","direction":"CREDIT","channel":"CASH",
             "amount":9500.00,"currency":"EUR","bookedAt":"2026-09-23T09:00:00Z"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TransactionIngestService ingestService;

    @Test
    void aNewTransactionIsCreatedAndAReplayIsOk() throws Exception {
        TransactionView view = new TransactionView(UUID.randomUUID(), "REF-1", "C-10001",
                Direction.CREDIT, Channel.CASH, new BigDecimal("9500.00"), "EUR", null, null,
                Instant.parse("2026-09-23T09:00:00Z"), Instant.parse("2026-09-23T09:00:01Z"));

        when(ingestService.ingest(any())).thenReturn(new IngestResult(view, true));
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalRef").value("REF-1"));

        when(ingestService.ingest(any())).thenReturn(new IngestResult(view, false));
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk());
    }

    @Test
    void reportsEveryInvalidFieldAndNeverReachesTheService() throws Exception {
        String invalid = """
                {"externalRef":"REF-1","customerId":"C-10001","direction":"CREDIT","channel":"CASH",
                 "amount":-5,"currency":"JPY","bookedAt":"2026-09-23T09:00:00Z"}
                """;

        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.currency").value("only EUR is supported until FX conversion exists"));

        verifyNoInteractions(ingestService);
    }
}
