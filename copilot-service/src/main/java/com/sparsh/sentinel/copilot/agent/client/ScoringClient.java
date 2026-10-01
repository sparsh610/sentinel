package com.sparsh.sentinel.copilot.agent.client;

import com.sparsh.sentinel.copilot.agent.AgentProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * scoring-service, seen from the agent. copilot-service never reads scoring's tables: the alert
 * and its lifecycle belong to scoring-service, and this is its public API.
 */
@Component
public class ScoringClient {

    private final RestClient http;

    public ScoringClient(RestClient.Builder builder, AgentProperties properties) {
        this.http = builder.baseUrl(properties.scoringUrl()).build();
    }

    public AlertSnapshot alert(UUID alertId) {
        return reachable(() -> http.get().uri("/api/alerts/{id}", alertId).retrieve().body(AlertSnapshot.class));
    }

    public PeerSegment peerSegment(UUID transactionId) {
        return reachable(() -> http.get().uri("/api/transactions/{id}/peer-segment", transactionId)
                .retrieve().body(PeerSegment.class));
    }

    public void moveAlert(UUID alertId, String status) {
        reachable(() -> http.patch().uri("/api/alerts/{id}/status", alertId)
                .body(Map.of("status", status))
                .retrieve()
                .toBodilessEntity());
    }

    private static <T> T reachable(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException e) {
            throw new ServiceUnavailableException("scoring-service", e);
        }
    }

    public record AlertSnapshot(UUID id, UUID transactionId, String customerId, String customerSegment,
                                String direction, String channel, BigDecimal amount, String currency,
                                String counterpartyName, String counterpartyCountry, Instant bookedAt,
                                BigDecimal score, String status, List<Finding> findings) {

        public record Finding(String rule, BigDecimal score, String reason) {
        }
    }

    public record PeerSegment(UUID transactionId, long segment, double anomalyScore,
                              double threshold, boolean unusual) {
    }
}
