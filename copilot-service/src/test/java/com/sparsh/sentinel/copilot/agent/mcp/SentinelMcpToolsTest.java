package com.sparsh.sentinel.copilot.agent.mcp;

import com.sparsh.sentinel.copilot.agent.InvestigationService;
import com.sparsh.sentinel.copilot.agent.InvestigationTools;
import com.sparsh.sentinel.copilot.agent.client.ScoringClient;
import com.sparsh.sentinel.copilot.audit.AuditEvent;
import com.sparsh.sentinel.copilot.audit.AuditLog;
import com.sparsh.sentinel.copilot.privacy.PiiMasker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import com.sparsh.sentinel.copilot.agent.Evidence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SentinelMcpToolsTest {

    @Mock private ScoringClient scoring;
    @Mock private InvestigationTools tools;
    @Mock private InvestigationService investigations;
    @Mock private AuditLog audit;

    private SentinelMcpTools mcp() {
        return new SentinelMcpTools(scoring, tools, investigations, audit, new PiiMasker("test-key"));
    }

    private List<String> publishedNames() {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(mcp()).build().getToolCallbacks();
        return Arrays.stream(callbacks).map(c -> c.getToolDefinition().name()).toList();
    }

    @Test
    void publishesTheInvestigationToolsAndNothingElse() {
        assertThat(publishedNames()).containsExactlyInAnyOrder("listOpenAlerts", "riskScore", "customerHistory",
                "peerSegment", "policyLookup", "draftCaseNote", "getInvestigation");
    }

    /**
     * The line design decision §8 draws: an MCP client may gather evidence and ask for a draft,
     * never decide. A tool added here with a deciding name fails the build until it is argued for.
     */
    @Test
    void noPublishedToolCanDecideACase() {
        assertThat(publishedNames()).allSatisfy(name -> assertThat(name.toLowerCase(Locale.ROOT))
                .doesNotContain("approve", "escalate", "close", "decide", "decision", "report", "sign"));
    }

    @Test
    void identifiersFromTheModelAreCheckedBeforeAnythingIsCalled() {
        assertThatThrownBy(() -> mcp().riskScore("alert 42"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alertId must be a UUID");
        verifyNoInteractions(tools);
    }

    @Test
    void delegatesToTheSameToolsTheAgentUses() {
        UUID alert = UUID.randomUUID();

        mcp().riskScore(" " + alert + " ");
        mcp().draftCaseNote(alert.toString());

        verify(tools).riskScore(alert);
        verify(investigations).start(alert, AuditEvent.Channel.MCP);
    }

    @Test
    void everyCallIsAuditedWhetherItWorksOrNot() {
        UUID alert = UUID.randomUUID();

        mcp().riskScore(alert.toString());
        assertThatThrownBy(() -> mcp().peerSegment("not-a-uuid")).isInstanceOf(IllegalArgumentException.class);

        verify(audit).record(AuditEvent.Channel.MCP, "TOOL_CALL riskScore", alert.toString(), AuditEvent.Outcome.OK, null);
        verify(audit).refused(org.mockito.ArgumentMatchers.eq(AuditEvent.Channel.MCP),
                org.mockito.ArgumentMatchers.eq("TOOL_CALL peerSegment"), org.mockito.ArgumentMatchers.eq("not-a-uuid"),
                org.mockito.ArgumentMatchers.eq(AuditEvent.Outcome.FAILED),
                org.mockito.ArgumentMatchers.contains("transactionId must be a UUID"));
    }

    /** What leaves for the client's hosted model: tokens, never the counterparty's name. */
    @Test
    void anAlertLeavesWithItsCounterpartyAsAToken() {
        UUID alert = UUID.randomUUID();
        when(tools.riskScore(alert)).thenReturn(new ScoringClient.AlertSnapshot(alert, UUID.randomUUID(), "C-20001",
                "BUSINESS", "DEBIT", "TRANSFER", new BigDecimal("9500"), "EUR", "Kessler Bau GmbH", "IR",
                Instant.parse("2026-10-15T09:00:00Z"), new BigDecimal("0.9"), "OPEN",
                List.of(new ScoringClient.AlertSnapshot.Finding("HIGH_RISK_JURISDICTION", new BigDecimal("0.9"),
                        "Transfer to Kessler Bau GmbH in IR"))));

        ScoringClient.AlertSnapshot sent = mcp().riskScore(alert.toString());

        assertThat(sent.counterpartyName()).startsWith("PARTY-");
        assertThat(sent.findings().getFirst().reason()).isEqualTo("Transfer to " + sent.counterpartyName() + " in IR");
        assertThat(sent.customerId()).isEqualTo("C-20001");
    }

    @Test
    void theSamePartyIsTheSameTokenInEveryResult() {
        when(tools.customerHistory("C-20001")).thenReturn(new Evidence.History(2, new BigDecimal("100"),
                BigDecimal.ZERO, 0, 1, Set.of("DE"), List.of("2026-10-14 credit transfer EUR 100 with Kessler Bau GmbH"),
                Set.of("Kessler Bau GmbH")));
        UUID alert = UUID.randomUUID();
        when(tools.riskScore(alert)).thenReturn(new ScoringClient.AlertSnapshot(alert, UUID.randomUUID(), "C-20001",
                "BUSINESS", "CREDIT", "TRANSFER", new BigDecimal("100"), "EUR", "Kessler Bau GmbH", "DE",
                Instant.parse("2026-10-14T09:00:00Z"), new BigDecimal("0.5"), "OPEN", List.of()));

        Evidence.History history = mcp().customerHistory("C-20001");
        String token = mcp().riskScore(alert.toString()).counterpartyName();

        assertThat(history.recent().getFirst()).endsWith("with " + token).doesNotContain("Kessler");
        assertThat(history.counterpartyNames()).containsExactly(token);
    }

    @Test
    void theAlertListIsCapped() {
        mcp().listOpenAlerts(10_000);
        mcp().listOpenAlerts(null);

        verify(scoring).openAlerts(50);
        verify(scoring).openAlerts(20);
    }
}
