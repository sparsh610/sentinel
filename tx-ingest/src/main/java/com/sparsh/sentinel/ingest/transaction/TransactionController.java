package com.sparsh.sentinel.ingest.transaction;

import com.sparsh.sentinel.ingest.transaction.TransactionIngestService.IngestResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api")
public class TransactionController {

    private final TransactionIngestService ingestService;

    public TransactionController(TransactionIngestService ingestService) {
        this.ingestService = ingestService;
    }

    /**
     * Accepts one transaction.
     *
     * <p>201 when it is new, 200 when the {@code externalRef} was seen before. A sender that
     * timed out and retries gets the original back instead of an error it has to interpret.
     */
    @PostMapping("/transactions")
    public ResponseEntity<TransactionView> ingest(@Valid @RequestBody TransactionRequest request) {
        IngestResult result = ingestService.ingest(request);
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.transaction());
    }

    @GetMapping("/customers/{customerId}/transactions")
    public List<TransactionView> recent(@PathVariable String customerId,
                                        @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        return ingestService.recentForCustomer(customerId, limit);
    }
}
