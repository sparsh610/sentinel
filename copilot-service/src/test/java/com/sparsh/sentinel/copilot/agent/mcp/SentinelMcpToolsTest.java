package com.sparsh.sentinel.copilot.agent.mcp;

import com.sparsh.sentinel.copilot.agent.InvestigationService;
import com.sparsh.sentinel.copilot.agent.InvestigationTools;
import com.sparsh.sentinel.copilot.agent.client.ScoringClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SentinelMcpToolsTest {

    @Mock private ScoringClient scoring;
    @Mock private InvestigationTools tools;
    @Mock private InvestigationService investigations;

    private SentinelMcpTools mcp() {
        return new SentinelMcpTools(scoring, tools, investigations);
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
        verify(investigations).start(alert);
    }

    @Test
    void theAlertListIsCapped() {
        mcp().listOpenAlerts(10_000);
        mcp().listOpenAlerts(null);

        verify(scoring).openAlerts(50);
        verify(scoring).openAlerts(20);
    }
}
