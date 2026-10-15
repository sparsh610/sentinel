package com.sparsh.sentinel.copilot.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sparsh.sentinel.copilot.agent.client.ScoringClient.AlertSnapshot;
import com.sparsh.sentinel.copilot.agent.client.ScoringClient.PeerSegment;
import com.sparsh.sentinel.copilot.chat.Citation;
import com.sparsh.sentinel.copilot.document.Classification;
import com.sparsh.sentinel.copilot.privacy.MaskingSession;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * What the tools found for one investigation, filled in as they run. The case note is written
 * from this and nothing else - not from the model's general knowledge.
 *
 * <p>The model sees it only through {@link #describeForModel()}: counterparty names, IBANs, card
 * numbers and e-mail addresses replaced by tokens (design decision §6). The analyst's copy of the
 * note has them put back.
 */
public final class Evidence {

    /** Masks what goes to the model for this run, and unmasks what comes back. */
    final MaskingSession privacy;

    AlertSnapshot alert;
    History history;
    PeerSegment peerSegment;
    final List<Citation> policy = new ArrayList<>();
    /** What could not be established, and why - the note has to say so rather than guess. */
    final List<String> gaps = new ArrayList<>();
    /** The policy the person this run is for may see; the policy lookup is held to it. */
    Set<Classification> clearance = EnumSet.of(Classification.PUBLIC);

    Evidence(MaskingSession privacy) {
        this.privacy = privacy;
    }

    /**
     * @param recent            the newest few, for the note; the totals cover everything read.
     * @param counterpartyNames who the customer dealt with - kept so they can be masked wherever
     *                          they appear, and never written to the trace or a tool result.
     */
    public record History(int transactions, BigDecimal moneyIn, BigDecimal moneyOut, int cashTransactions,
                   int distinctCounterparties, Set<String> counterpartyCountries, List<String> recent,
                   @JsonIgnore Set<String> counterpartyNames) {
    }

    /** The evidence as the model may see it. */
    String describeForModel() {
        return forModel(describe());
    }

    /** Any text about this evidence, masked for the model - including tool results the LLM planner reads. */
    String forModel(String text) {
        if (alert != null) {
            privacy.party(alert.counterpartyName());
        }
        if (history != null && history.counterpartyNames() != null) {
            history.counterpartyNames().forEach(privacy::party);
        }
        return privacy.mask(text);
    }

    /** Adds excerpts not already held, renumbering so the markers stay 1, 2, 3 across lookups. */
    void addPolicy(List<Citation> found) {
        for (Citation c : found) {
            boolean held = policy.stream().anyMatch(p ->
                    java.util.Objects.equals(p.documentId(), c.documentId()) && p.chunkIndex() == c.chunkIndex());
            if (!held) {
                policy.add(new Citation(policy.size() + 1, c.documentId(), c.title(), c.chunkIndex(), c.score(),
                        c.excerpt()));
            }
        }
    }

    /** The evidence as plain text, numbered so the note can cite policy as [1], [2]. */
    String describe() {
        StringBuilder sb = new StringBuilder();
        if (alert != null) {
            sb.append("ALERTED TRANSACTION (one transaction; the amount is this transaction's alone)\n")
              .append("Customer ").append(alert.customerId()).append(" (").append(alert.customerSegment()).append("), ")
              .append(alert.direction().toLowerCase()).append(' ').append(alert.channel().toLowerCase()).append(" of ")
              .append(alert.currency()).append(' ').append(alert.amount().toPlainString());
            if (alert.counterpartyName() != null) {
                sb.append(" with ").append(alert.counterpartyName());
            }
            if (alert.counterpartyCountry() != null) {
                sb.append(" (").append(alert.counterpartyCountry()).append(')');
            }
            sb.append(", booked ").append(alert.bookedAt()).append('\n')
              .append("\nWHY IT ALERTED\n");
            for (var finding : alert.findings()) {
                sb.append("- ").append(finding.rule()).append(" (score ").append(finding.score().toPlainString())
                  .append("): ").append(finding.reason()).append('\n');
            }
        }
        if (history != null) {
            sb.append("\nCUSTOMER HISTORY (last ").append(history.transactions()).append(" transactions)\n")
              .append("Money in ").append(history.moneyIn().toPlainString())
              .append(", money out ").append(history.moneyOut().toPlainString())
              .append(", cash transactions ").append(history.cashTransactions())
              .append(", distinct counterparties ").append(history.distinctCounterparties())
              .append(", counterparty countries ").append(history.counterpartyCountries()).append('\n');
            history.recent().forEach(line -> sb.append("- ").append(line).append('\n'));
        }
        if (peerSegment != null) {
            sb.append("\nPEER SEGMENT\nSegment ").append(peerSegment.segment())
              .append(peerSegment.unusual() ? ": UNUSUAL for its segment" : ": ordinary for its segment")
              .append(" (anomaly score %.3f, segment threshold %.3f)".formatted(
                      peerSegment.anomalyScore(), peerSegment.threshold()))
              .append('\n');
        }
        if (!policy.isEmpty()) {
            sb.append("\nPOLICY\n");
            for (Citation citation : policy) {
                sb.append('[').append(citation.marker()).append("] ").append(citation.title());
                List<String> clauses = clauseNumbers(citation.excerpt());
                if (!clauses.isEmpty()) {
                    sb.append(" (clauses ").append(String.join(", ", clauses)).append(')');
                }
                sb.append('\n').append(citation.excerpt().strip()).append('\n');
            }
        }
        if (!gaps.isEmpty()) {
            sb.append("\nNOT ESTABLISHED\n");
            gaps.forEach(gap -> sb.append("- ").append(gap).append('\n'));
        }
        return sb.toString();
    }

    private static final java.util.regex.Pattern CLAUSE =
            java.util.regex.Pattern.compile("\\b[A-Z]{2,5}-\\d{2}\\.\\d+\\b");

    /**
     * "AML-04.2"-style clause numbers in an excerpt, in order. Named up front, so a small model
     * cites the clause rather than only the document.
     */
    static List<String> clauseNumbers(String excerpt) {
        return CLAUSE.matcher(excerpt).results().map(java.util.regex.MatchResult::group).distinct().toList();
    }
}
