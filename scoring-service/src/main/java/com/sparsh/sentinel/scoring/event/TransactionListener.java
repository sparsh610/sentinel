package com.sparsh.sentinel.scoring.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparsh.sentinel.scoring.score.ScoringService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the transaction stream. Parsing and version checks only - scoring lives in
 * {@link ScoringService}, which can be tested without a broker.
 */
@Component
public class TransactionListener {

    private final ObjectMapper objectMapper;
    private final ScoringService scoringService;

    public TransactionListener(ObjectMapper objectMapper, ScoringService scoringService) {
        this.objectMapper = objectMapper;
        this.scoringService = scoringService;
    }

    @KafkaListener(topics = "${sentinel.scoring.topic}")
    public void onTransaction(String payload) throws JsonProcessingException {
        TransactionEvent event = objectMapper.readValue(payload, TransactionEvent.class);

        if (event.schemaVersion() != TransactionEvent.SUPPORTED_SCHEMA_VERSION) {
            throw new UnsupportedEventException("Schema version " + event.schemaVersion()
                    + " is not supported; this service reads version "
                    + TransactionEvent.SUPPORTED_SCHEMA_VERSION);
        }

        scoringService.score(event);
    }
}
