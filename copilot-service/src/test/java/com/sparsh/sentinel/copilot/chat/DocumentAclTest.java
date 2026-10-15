package com.sparsh.sentinel.copilot.chat;

import com.sparsh.sentinel.copilot.config.CopilotProperties;
import com.sparsh.sentinel.copilot.document.Classification;
import com.sparsh.sentinel.copilot.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The document ACL, applied inside the vector search rather than to its results. */
@ExtendWith(MockitoExtension.class)
class DocumentAclTest {

    @Mock private ChatClient chatClient;
    @Mock private VectorStore vectorStore;
    @Mock private com.sparsh.sentinel.copilot.audit.AuditLog audit;

    private final CopilotProperties properties = new CopilotProperties(220, 150, 6, 0.55);

    private CopilotChatService service() {
        return new CopilotChatService(chatClient, vectorStore, properties, audit,
                new com.sparsh.sentinel.copilot.privacy.PiiMasker("test-key"));
    }

    private String filterFor(Set<String> roles) {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        service().retrieve("structuring threshold", CurrentUser.clearanceFor(roles));
        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(request.capture());
        assertThat(request.getValue().getFilterExpression()).isNotNull();
        return request.getValue().getFilterExpression().toString();
    }

    @Test
    void anAnalystsSearchNeverMatchesConfidentialChunks() {
        assertThat(filterFor(Set.of(CurrentUser.ANALYST))).contains("PUBLIC", "INTERNAL").doesNotContain("CONFIDENTIAL");
    }

    @Test
    void aSeniorApproverMaySeeConfidentialPolicy() {
        assertThat(filterFor(Set.of(CurrentUser.SENIOR_APPROVER))).contains("PUBLIC", "INTERNAL", "CONFIDENTIAL");
    }

    @Test
    void theClearanceFollowsTheRoles() {
        assertThat(CurrentUser.clearanceFor(Set.of())).containsExactly(Classification.PUBLIC);
        assertThat(CurrentUser.clearanceFor(Set.of(CurrentUser.ANALYST)))
                .containsExactlyInAnyOrder(Classification.PUBLIC, Classification.INTERNAL);
    }

    @Test
    void noClearanceNoSearch() {
        assertThat(service().retrieve("anything", Set.of())).isEmpty();
        verifyNoInteractions(vectorStore);
    }
}
