package com.sparsh.sentinel.copilot.agent.client;

import com.sparsh.sentinel.copilot.agent.AgentProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** tx-ingest, seen from the agent: the customer's recent transactions, newest first. */
@Component
public class IngestClient {

    private final RestClient http;

    public IngestClient(RestClient.Builder builder, AgentProperties properties) {
        this.http = builder.baseUrl(properties.ingestUrl()).build();
    }

    public List<Transaction> recent(String customerId, int limit) {
        try {
            return http.get()
                    .uri(uri -> uri.path("/api/customers/{id}/transactions").queryParam("limit", limit).build(customerId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
        } catch (ResourceAccessException e) {
            throw new ServiceUnavailableException("tx-ingest", e);
        }
    }

    public record Transaction(String direction, String channel, BigDecimal amount, String currency,
                              String counterpartyName, String counterpartyCountry, Instant bookedAt) {
    }
}
