package com.sparsh.sentinel.copilot.agent;

import com.sparsh.sentinel.copilot.agent.client.IngestClient;
import com.sparsh.sentinel.copilot.agent.client.ScoringClient;
import com.sparsh.sentinel.copilot.agent.client.ScoringClient.AlertSnapshot;
import com.sparsh.sentinel.copilot.agent.client.ScoringClient.PeerSegment;
import com.sparsh.sentinel.copilot.chat.Citation;
import com.sparsh.sentinel.copilot.chat.CopilotChatService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The evidence tools. Plain methods with typed inputs and outputs, so the fixed plan and the LLM
 * planner run exactly the same code, and week 6 can publish them over MCP without changing them.
 *
 * <p>They read; none of them writes. Nothing an investigation's tools do can change an alert,
 * a transaction or a decision.
 */
@Component
public class InvestigationTools {

    static final String RISK_SCORE = "riskScore";
    static final String CUSTOMER_HISTORY = "customerHistory";
    static final String PEER_SEGMENT = "peerSegment";
    static final String POLICY_LOOKUP = "policyLookup";

    private static final int RECENT_IN_NOTE = 8;
    private static final int POLICY_EXCERPTS = 3;

    private final ScoringClient scoring;
    private final IngestClient ingest;
    private final CopilotChatService retrieval;
    private final AgentProperties properties;

    public InvestigationTools(ScoringClient scoring, IngestClient ingest, CopilotChatService retrieval,
                              AgentProperties properties) {
        this.scoring = scoring;
        this.ingest = ingest;
        this.retrieval = retrieval;
        this.properties = properties;
    }

    /** The alert, its findings and their scores. */
    public AlertSnapshot riskScore(UUID alertId) {
        return scoring.alert(alertId);
    }

    /** The customer's recent transactions, summarised the way an analyst reads them. */
    public Evidence.History customerHistory(String customerId) {
        List<IngestClient.Transaction> recent = ingest.recent(customerId, properties.historyLimit());

        BigDecimal in = BigDecimal.ZERO;
        BigDecimal out = BigDecimal.ZERO;
        int cash = 0;
        Set<String> countries = new TreeSet<>();
        for (IngestClient.Transaction t : recent) {
            if ("CREDIT".equals(t.direction())) {
                in = in.add(t.amount());
            } else {
                out = out.add(t.amount());
            }
            if ("CASH".equals(t.channel())) {
                cash++;
            }
            if (t.counterpartyCountry() != null) {
                countries.add(t.counterpartyCountry());
            }
        }
        int counterparties = (int) recent.stream().map(IngestClient.Transaction::counterpartyName)
                .filter(Objects::nonNull).distinct().count();
        List<String> lines = recent.stream().limit(RECENT_IN_NOTE)
                .map(t -> "%s %s %s %s %s%s".formatted(t.bookedAt(), t.direction().toLowerCase(),
                        t.channel().toLowerCase(), t.currency(), t.amount().toPlainString(),
                        t.counterpartyName() == null ? "" : " with " + t.counterpartyName()))
                .toList();
        return new Evidence.History(recent.size(), in, out, cash, counterparties, countries, lines);
    }

    /** Where the alerted transaction sits among its peers, from scoring-service's models. */
    public PeerSegment peerSegment(UUID transactionId) {
        return scoring.peerSegment(transactionId);
    }

    /** The policy clauses that match the question, best first. */
    public List<Citation> policyLookup(String question) {
        return retrieval.retrieve(question).stream().limit(POLICY_EXCERPTS).toList();
    }

    /**
     * The fixed plan's search phrase for an alert: what an analyst would look up for these
     * findings. Phrased in the policy's own vocabulary, because retrieval matches meaning, and the
     * closer the words the higher the similarity.
     */
    static String policyQuestionFor(AlertSnapshot alert) {
        Set<String> rules = new TreeSet<>();
        alert.findings().forEach(f -> rules.add(f.rule()));
        StringBuilder q = new StringBuilder("suspicious activity report");
        if (rules.contains("STRUCTURING")) {
            q.append(", structuring: several smaller transactions to avoid a reporting threshold");
        }
        if (rules.contains("LARGE_CASH")) {
            q.append(", large cash transaction above the EUR 10,000 threshold");
        }
        if (rules.contains("HIGH_RISK_JURISDICTION")) {
            q.append(", enhanced due diligence for a high-risk third country");
        }
        if (rules.contains("ML_CLASSIFIER") || rules.contains("ML_ANOMALY")) {
            q.append(", transaction monitoring alert disposition");
        }
        return q.toString();
    }
}
