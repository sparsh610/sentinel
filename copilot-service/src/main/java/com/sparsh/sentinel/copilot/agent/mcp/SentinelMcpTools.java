package com.sparsh.sentinel.copilot.agent.mcp;

import com.sparsh.sentinel.copilot.agent.Evidence;
import com.sparsh.sentinel.copilot.agent.InvestigationService;
import com.sparsh.sentinel.copilot.agent.InvestigationTools;
import com.sparsh.sentinel.copilot.agent.InvestigationView;
import com.sparsh.sentinel.copilot.agent.client.ScoringClient;
import com.sparsh.sentinel.copilot.agent.client.ScoringClient.AlertSnapshot;
import com.sparsh.sentinel.copilot.agent.client.ScoringClient.PeerSegment;
import com.sparsh.sentinel.copilot.audit.AuditEvent.Channel;
import com.sparsh.sentinel.copilot.audit.AuditEvent.Outcome;
import com.sparsh.sentinel.copilot.audit.AuditLog;
import com.sparsh.sentinel.copilot.chat.Citation;
import com.sparsh.sentinel.copilot.privacy.MaskingSession;
import com.sparsh.sentinel.copilot.privacy.PiiMasker;
import com.sparsh.sentinel.copilot.security.CurrentUser;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The investigation tools as an MCP client sees them - a thin adapter over the same
 * {@link InvestigationTools} and {@link InvestigationService} the internal agent uses. Nothing
 * here is reimplemented; MCP is a transport in front of them.
 *
 * <p><b>No tool here can decide a case.</b> They read, and {@code draftCaseNote} drafts. Escalating,
 * closing and signing off stay in the console, behind a named person (design decision §8). A test
 * fails if a tool whose name suggests a decision is ever added.
 *
 * <p><b>Everything returned is masked.</b> The client on the other end is a hosted model, so
 * counterparty names, IBANs, card numbers and e-mail addresses leave as stable tokens
 * ({@link PiiMasker}): the same party is the same token in every result and in the case note, so
 * the model can still connect them without learning who they are.
 *
 * <p>Every call is an {@code audit_event} - who, which tool, with what, and whether it was allowed -
 * written in the name on the client's token.
 */
@Component
public class SentinelMcpTools {

    private static final int MAX_ALERTS = 50;

    private final ScoringClient scoring;
    private final InvestigationTools tools;
    private final InvestigationService investigations;
    private final AuditLog audit;
    private final PiiMasker masker;

    public SentinelMcpTools(ScoringClient scoring, InvestigationTools tools, InvestigationService investigations,
                            AuditLog audit, PiiMasker masker) {
        this.scoring = scoring;
        this.tools = tools;
        this.investigations = investigations;
        this.audit = audit;
        this.masker = masker;
    }

    @Tool(name = "listOpenAlerts", resultConverter = IsoDateResultConverter.class, description = """
            The open alert queue, most serious first: each alert's id, customer, amount, channel,
            counterparty and the findings (rules and models) that raised it. Start here.""")
    public List<AlertSnapshot> listOpenAlerts(
            @ToolParam(description = "How many alerts, 1-50", required = false) Integer limit) {
        int n = limit == null ? 20 : Math.max(1, Math.min(limit, MAX_ALERTS));
        return audited("listOpenAlerts", null, "limit=" + n, () -> {
            MaskingSession privacy = masker.session();
            return scoring.openAlerts(n).stream().map(a -> masked(a, privacy)).toList();
        });
    }

    @Tool(name = "riskScore", resultConverter = IsoDateResultConverter.class, description = """
            One alert in full: the transaction it was raised on, its overall score, and every finding
            with its score and the reason it fired.""")
    public AlertSnapshot riskScore(@ToolParam(description = "The alert's id (a UUID)") String alertId) {
        return audited("riskScore", alertId, null,
                () -> masked(tools.riskScore(uuid(alertId, "alertId")), masker.session()));
    }

    @Tool(name = "customerHistory", resultConverter = IsoDateResultConverter.class, description = """
            A customer's recent transactions summarised: count, money in and out, cash
            transactions, distinct counterparties and their countries, and the newest few.""")
    public Evidence.History customerHistory(
            @ToolParam(description = "The customer's id, e.g. C-20001") String customerId) {
        return audited("customerHistory", customerId, null, () -> masked(tools.customerHistory(customerId)));
    }

    @Tool(name = "peerSegment", resultConverter = IsoDateResultConverter.class, description = """
            Where a transaction sits among its peers: the KMeans peer segment, the Isolation Forest
            anomaly score (lower is more unusual), the segment's own threshold, and whether the
            transaction is unusual for that segment. Use the alert's transactionId.""")
    public PeerSegment peerSegment(
            @ToolParam(description = "The transaction's id (a UUID) - the alert's transactionId") String transactionId) {
        return audited("peerSegment", transactionId, null,
                () -> tools.peerSegment(uuid(transactionId, "transactionId")));
    }

    @Tool(name = "policyLookup", resultConverter = IsoDateResultConverter.class, description = """
            Search the bank's anti-money-laundering policy. Returns the best-matching excerpts with
            their clause text, so an answer can cite the clause (e.g. AML-04.2).""")
    public List<Citation> policyLookup(
            @ToolParam(description = "What to look up, e.g. 'structuring reporting threshold'") String question) {
        return audited("policyLookup", null, question,
                () -> tools.policyLookup(question, CurrentUser.get().clearance()).stream()
                        .map(c -> new Citation(c.marker(), c.documentId(), c.title(), c.chunkIndex(), c.score(),
                                masker.maskPatterns(c.excerpt())))
                        .toList());
    }

    @Tool(name = "draftCaseNote", resultConverter = IsoDateResultConverter.class, description = """
            Runs Sentinel's own investigation agent on an alert and drafts a case note for a human
            analyst, or returns the investigation already under way for it. The run takes about a
            minute; while its status is RUNNING, call getInvestigation with the returned id.
            This only drafts - it cannot escalate, close or report a case; people do that.""")
    public InvestigationView draftCaseNote(@ToolParam(description = "The alert's id (a UUID)") String alertId) {
        return audited("draftCaseNote", alertId, null,
                () -> forModel(investigations.start(uuid(alertId, "alertId"), Channel.MCP)));
    }

    @Tool(name = "getInvestigation", resultConverter = IsoDateResultConverter.class, description = """
            An investigation's current state: its status, the execution trace of every tool call,
            the drafted case note once ready, and any decisions people have taken.""")
    public InvestigationView getInvestigation(
            @ToolParam(description = "The investigation's id (a UUID), from draftCaseNote") String investigationId) {
        return audited("getInvestigation", investigationId, null,
                () -> forModel(investigations.get(uuid(investigationId, "investigationId"))));
    }

    /** The alert with its counterparty as a token - in its own field and inside the findings' reasons. */
    private static AlertSnapshot masked(AlertSnapshot a, MaskingSession privacy) {
        if (a == null) {
            return null;
        }
        String party = privacy.party(a.counterpartyName());
        List<AlertSnapshot.Finding> findings = a.findings() == null ? null : a.findings().stream()
                .map(f -> new AlertSnapshot.Finding(f.rule(), f.score(), privacy.mask(f.reason())))
                .toList();
        return new AlertSnapshot(a.id(), a.transactionId(), a.customerId(), a.customerSegment(), a.direction(),
                a.channel(), a.amount(), a.currency(), party, a.counterpartyCountry(), a.bookedAt(), a.score(),
                a.status(), findings);
    }

    /** The history with every counterparty as a token, in the summary lines as well. */
    private Evidence.History masked(Evidence.History h) {
        if (h == null) {
            return null;
        }
        MaskingSession privacy = masker.session();
        Set<String> tokens = new TreeSet<>();
        if (h.counterpartyNames() != null) {
            h.counterpartyNames().forEach(name -> tokens.add(privacy.party(name)));
        }
        return new Evidence.History(h.transactions(), h.moneyIn(), h.moneyOut(), h.cashTransactions(),
                h.distinctCounterparties(), h.counterpartyCountries(),
                h.recent().stream().map(privacy::mask).toList(), tokens);
    }

    private static InvestigationView forModel(InvestigationView view) {
        return view == null ? null : view.forModel();
    }

    /** Runs one tool call and writes its audit line - OK, DENIED by a role check, or FAILED. */
    private <T> T audited(String tool, String target, String detail, Supplier<T> call) {
        String action = "TOOL_CALL " + tool;
        try {
            T result = call.get();
            audit.record(Channel.MCP, action, target, Outcome.OK, detail);
            return result;
        } catch (AccessDeniedException e) {
            audit.refused(Channel.MCP, action, target, Outcome.DENIED, detail);
            throw e;
        } catch (RuntimeException e) {
            audit.refused(Channel.MCP, action, target, Outcome.FAILED,
                    (detail == null ? "" : detail + " - ") + e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
    }

    private static UUID uuid(String value, String name) {
        try {
            return UUID.fromString(value.strip());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(name + " must be a UUID, got '" + value + "'");
        }
    }
}
