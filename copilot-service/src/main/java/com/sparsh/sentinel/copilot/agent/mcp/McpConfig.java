package com.sparsh.sentinel.copilot.agent.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Hands the tools to the MCP server - only when it is on (the "mcp" profile), so the default run
 * publishes nothing beyond the REST API.
 *
 * <p>They go to the server as MCP tool specifications, not as a {@code ToolCallbackProvider}
 * bean. Spring AI collects every provider bean into the tool resolver its own chat models use,
 * which would make these tools callable by Sentinel's internal models as well - and, since the
 * tools reach the agent and the agent reaches the chat model, tie the context in a cycle. The MCP
 * server is the one place they are published.
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "enabled", havingValue = "true")
public class McpConfig {

    @Bean
    List<McpServerFeatures.SyncToolSpecification> sentinelTools(SentinelMcpTools tools) {
        return McpToolUtils.toSyncToolSpecification(List.of(ToolCallbacks.from(tools)));
    }
}
