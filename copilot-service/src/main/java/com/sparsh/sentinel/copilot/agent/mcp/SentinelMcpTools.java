package com.sparsh.sentinel.copilot.agent.mcp;

import com.sparsh.sentinel.copilot.agent.Evidence;
import com.sparsh.sentinel.copilot.agent.InvestigationService;
import com.sparsh.sentinel.copilot.agent.InvestigationTools;
import com.sparsh.sentinel.copilot.agent.InvestigationView;
import com.sparsh.sentinel.copilot.agent.client.ScoringClient;
import com.sparsh.sentinel.copilot.agent.client.ScoringClient.AlertSnapshot;
import com.sparsh.sentinel.copilot.agent.client.ScoringClient.PeerSegment;
import com.sparsh.sentinel.copilot.chat.Citation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * The investigation tools as an MCP client sees them - a thin adapter over the same
 * {@link InvestigationTools} and {@link InvestigationService} the internal agent uses. Nothing
 * here is reimplemented; MCP is a transport in front of them.
 *
 * <p><b>No tool here can decide a case.</b> They read, and {@code draftCaseNote} drafts. Escalating,
 * closing and signing off stay in the console, behind a named person (design decision §8). A test
 * fails if a tool whose name suggests a decision is ever added.
 *
 * <p>Every call is logged with its arguments; week 7 turns that into an {@code audit_event}.
 */
@Component
public class SentinelMcpTools {

    private static final Logger log = LoggerFactory.getLogger(SentinelMcpTools.class);
    private static final int MAX_ALERTS = 50;

    private final ScoringClient scoring;
    private final InvestigationTools tools;
    private final InvestigationService investigations;

    public SentinelMcpTools(ScoringClient scoring, InvestigationTools tools, InvestigationService investigations) {
        this.scoring = scoring;
        this.tools = tools;
        this.investigations = investigations;
    }

    @Tool(name = "listOpenAlerts", resultConverter = IsoDateResultConverter.class, description = """
            The open alert queue, most serious first: each alert's id, customer, amount, channel,
            counterparty and the findings (rules and models) that raised it. Start here.""")
    public List<AlertSnapshot> listOpenAlerts(
            @ToolParam(description = "How many alerts, 1-50", required = false) Integer limit) {
        int n = limit == null ? 20 : Math.max(1, Math.min(limit, MAX_ALERTS));
        log.info("MCP listOpenAlerts(limit={})", n);
        return scoring.openAlerts(n);
    }

    @Tool(name = "riskScore", resultConverter = IsoDateResultConverter.class, description = """
            One alert in full: the transaction it was raised on, its overall score, and every finding
            with its score and the reason it fired.""")
    public AlertSnapshot riskScore(@ToolParam(description = "The alert's id (a UUID)") String alertId) {
        log.info("MCP riskScore({})", alertId);
        return tools.riskScore(uuid(alertId, "alertId"));
    }

    @Tool(name = "customerHistory", resultConverter = IsoDateResultConverter.class, description = """
            A customer's recent transactions summarised: count, money in and out, cash
            transactions, distinct counterparties and their countries, and the newest few.""")
    public Evidence.History customerHistory(
            @ToolParam(description = "The customer's id, e.g. C-20001") String customerId) {
        log.info("MCP customerHistory({})", customerId);
        return tools.customerHistory(customerId);
    }

    @Tool(name = "peerSegment", resultConverter = IsoDateResultConverter.class, description = """
            Where a transaction sits among its peers: the KMeans peer segment, the Isolation Forest
            anomaly score (lower is more unusual), the segment's own threshold, and whether the
            transaction is unusual for that segment. Use the alert's transactionId.""")
    public PeerSegment peerSegment(
            @ToolParam(description = "The transaction's id (a UUID) - the alert's transactionId") String transactionId) {
        log.info("MCP peerSegment({})", transactionId);
        return tools.peerSegment(uuid(transactionId, "transactionId"));
    }

    @Tool(name = "policyLookup", resultConverter = IsoDateResultConverter.class, description = """
            Search the bank's anti-money-laundering policy. Returns the best-matching excerpts with
            their clause text, so an answer can cite the clause (e.g. AML-04.2).""")
    public List<Citation> policyLookup(
            @ToolParam(description = "What to look up, e.g. 'structuring reporting threshold'") String question) {
        log.info("MCP policyLookup({})", question);
        return tools.policyLookup(question);
    }

    @Tool(name = "draftCaseNote", resultConverter = IsoDateResultConverter.class, description = """
            Runs Sentinel's own investigation agent on an alert and drafts a case note for a human
            analyst, or returns the investigation already under way for it. The run takes about a
            minute; while its status is RUNNING, call getInvestigation with the returned id.
            This only drafts - it cannot escalate, close or report a case; people do that.""")
    public InvestigationView draftCaseNote(@ToolParam(description = "The alert's id (a UUID)") String alertId) {
        log.info("MCP draftCaseNote({})", alertId);
        return investigations.start(uuid(alertId, "alertId"));
    }

    @Tool(name = "getInvestigation", resultConverter = IsoDateResultConverter.class, description = """
            An investigation's current state: its status, the execution trace of every tool call,
            the drafted case note once ready, and any decisions people have taken.""")
    public InvestigationView getInvestigation(
            @ToolParam(description = "The investigation's id (a UUID), from draftCaseNote") String investigationId) {
        log.info("MCP getInvestigation({})", investigationId);
        return investigations.get(uuid(investigationId, "investigationId"));
    }

    private static UUID uuid(String value, String name) {
        try {
            return UUID.fromString(value.strip());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(name + " must be a UUID, got '" + value + "'");
        }
    }
}
