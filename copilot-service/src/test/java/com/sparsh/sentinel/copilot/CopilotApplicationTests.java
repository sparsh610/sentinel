package com.sparsh.sentinel.copilot;

import com.sparsh.sentinel.copilot.chat.CopilotChatService;
import com.sparsh.sentinel.copilot.document.DocumentIngestionService;
import com.sparsh.sentinel.copilot.document.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context smoke test.
 *
 * <p>The database and the model endpoint are stubbed out: this asserts that the application's
 * own wiring is correct, not that Postgres and Ollama work. Retrieval against a real pgvector
 * instance is a Testcontainers test, scheduled for week 8.
 */
@SpringBootTest
class CopilotApplicationTests {

    @MockitoBean
    private VectorStore vectorStore;

    @MockitoBean
    private ChatModel chatModel;

    @MockitoBean
    private DocumentRepository documentRepository;

    @MockitoBean
    private com.sparsh.sentinel.copilot.agent.InvestigationRepository investigationRepository;

    @MockitoBean
    private com.sparsh.sentinel.copilot.agent.InvestigationStepRepository investigationStepRepository;

    @MockitoBean
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Autowired
    private com.sparsh.sentinel.copilot.agent.InvestigationService investigationService;

    @Autowired
    private DocumentIngestionService ingestionService;

    @Autowired
    private CopilotChatService chatService;

    @Test
    void contextLoads() {
        assertThat(ingestionService).isNotNull();
        assertThat(chatService).isNotNull();
        assertThat(investigationService).isNotNull();
    }
}
