package com.sparsh.sentinel.copilot.agent.mcp;

import com.sparsh.sentinel.copilot.agent.InvestigationRepository;
import com.sparsh.sentinel.copilot.agent.InvestigationStepRepository;
import com.sparsh.sentinel.copilot.document.DocumentRepository;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The context with the MCP server on, as the "mcp" profile runs it.
 *
 * <p>The chat model is deliberately the real Ollama one, not a mock: Spring AI's tool resolver
 * sits behind it, and publishing these tools the wrong way once tied the agent, the chat model
 * and that resolver in a cycle the mocked context could not see. Nothing here calls Ollama.
 */
@SpringBootTest(properties = {
        "spring.ai.mcp.server.enabled=true",
        "spring.ai.mcp.server.protocol=STREAMABLE",
})
class McpServerWiringTest {

    @MockitoBean private VectorStore vectorStore;
    @MockitoBean private DocumentRepository documentRepository;
    @MockitoBean private InvestigationRepository investigationRepository;
    @MockitoBean private InvestigationStepRepository investigationStepRepository;
    @MockitoBean private PlatformTransactionManager transactionManager;

    @Autowired
    private List<McpServerFeatures.SyncToolSpecification> sentinelTools;

    @Test
    void theServerStartsAndPublishesTheSevenTools() {
        assertThat(sentinelTools).extracting(spec -> spec.tool().name()).containsExactlyInAnyOrder(
                "listOpenAlerts", "riskScore", "customerHistory", "peerSegment", "policyLookup",
                "draftCaseNote", "getInvestigation");
    }
}
