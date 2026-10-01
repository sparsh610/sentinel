package com.sparsh.sentinel.copilot.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparsh.sentinel.copilot.agent.client.ScoringClient.AlertSnapshot;
import com.sparsh.sentinel.copilot.agent.client.ScoringClient.PeerSegment;
import com.sparsh.sentinel.copilot.agent.client.ServiceUnavailableException;
import com.sparsh.sentinel.copilot.chat.Citation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** One investigation run end to end, with the tools and the model faked. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentRunTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final UUID ALERT = UUID.randomUUID();
    private static final UUID TX = UUID.randomUUID();

    @Mock private InvestigationRepository investigations;
    @Mock private InvestigationStepRepository steps;
    @Mock private InvestigationTools tools;
    @Mock private CaseNoteWriter noteWriter;
    @Mock private ChatModel chatModel;
    @Mock private ToolCallingManager toolCallingManager;

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private Investigation investigation;

    private static final AlertSnapshot ALERT_SNAPSHOT = new AlertSnapshot(ALERT, TX, "C-20001", "BUSINESS",
            "CREDIT", "CASH", new BigDecimal("9800"), "EUR", null, null, NOW, new BigDecimal("0.85"), "IN_REVIEW",
            List.of(new AlertSnapshot.Finding("STRUCTURING", new BigDecimal("0.85"), "3 cash deposits in 24 hours")));

    @BeforeEach
    void wiring() {
        when(tools.riskScore(ALERT)).thenReturn(ALERT_SNAPSHOT);
        when(tools.customerHistory("C-20001")).thenReturn(new Evidence.History(3, new BigDecimal("29400"),
                BigDecimal.ZERO, 3, 0, Set.of(), List.of()));
        when(tools.peerSegment(TX)).thenReturn(new PeerSegment(TX, 0, -0.02, -0.12, false));
        when(tools.policyLookup(any())).thenReturn(List.of(
                new Citation(1, UUID.randomUUID(), "Internal AML Policy", 1, 0.8, "AML-04.2 Where three or more...")));
        when(noteWriter.write(any())).thenReturn(new CaseNoteWriter.CaseNote("Summary: ...", Investigation.NoteSource.MODEL));
        when(noteWriter.modelIsLocal()).thenReturn(true);
    }

    private void start(AgentProperties.Planner planner) {
        investigation = Investigation.start(ALERT, TX, "C-20001", new BigDecimal("9800"), "EUR", planner, NOW);
        when(investigations.findById(investigation.getId())).thenReturn(Optional.of(investigation));
    }

    private InvestigationRunner runner(int maxSteps) {
        AgentProperties properties = new AgentProperties(AgentProperties.Planner.FIXED, maxSteps, "qwen2.5:3b",
                "http://scoring", "http://ingest", 20);
        FixedPlanner fixed = new FixedPlanner(tools);
        LlmPlanner llm = new LlmPlanner(chatModel, toolCallingManager, fixed, properties);
        return new InvestigationRunner(investigations, steps, tools, fixed, llm, noteWriter, properties, json,
                mock(PlatformTransactionManager.class), clock);
    }

    private List<InvestigationStep> recordedSteps() {
        ArgumentCaptor<InvestigationStep> captor = ArgumentCaptor.forClass(InvestigationStep.class);
        verify(steps, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void theFixedPlanGathersEveryPieceOfEvidenceThenDraftsTheNote() {
        start(AgentProperties.Planner.FIXED);

        runner(6).run(investigation.getId());

        assertThat(recordedSteps()).extracting(InvestigationStep::getTool).containsExactly(
                "riskScore", "customerHistory", "peerSegment", "policyLookup", "draftCaseNote");
        assertThat(recordedSteps()).allMatch(s -> s.getStatus() == InvestigationStep.Status.OK);
        assertThat(investigation.getStatus()).isEqualTo(Investigation.Status.DRAFTED);
        assertThat(investigation.getNoteSource()).isEqualTo(Investigation.NoteSource.MODEL);
    }

    @Test
    void theStepLimitRefusesEvidenceButAlwaysLeavesRoomForTheNote() {
        start(AgentProperties.Planner.FIXED);

        runner(3).run(investigation.getId());

        assertThat(recordedSteps()).extracting(InvestigationStep::getTool, InvestigationStep::getStatus).containsExactly(
                org.assertj.core.groups.Tuple.tuple("riskScore", InvestigationStep.Status.OK),
                org.assertj.core.groups.Tuple.tuple("customerHistory", InvestigationStep.Status.OK),
                org.assertj.core.groups.Tuple.tuple("peerSegment", InvestigationStep.Status.REFUSED),
                org.assertj.core.groups.Tuple.tuple("policyLookup", InvestigationStep.Status.REFUSED),
                org.assertj.core.groups.Tuple.tuple("draftCaseNote", InvestigationStep.Status.OK));
        verify(tools, never()).peerSegment(any());
        assertThat(investigation.getStatus()).isEqualTo(Investigation.Status.DRAFTED);
    }

    @Test
    void aFailingToolIsRecordedAndTheNoteSaysWhatIsMissing() {
        start(AgentProperties.Planner.FIXED);
        when(tools.customerHistory(any())).thenThrow(new ServiceUnavailableException("tx-ingest", null));
        ArgumentCaptor<Evidence> evidence = ArgumentCaptor.forClass(Evidence.class);

        runner(6).run(investigation.getId());

        assertThat(recordedSteps()).filteredOn(s -> s.getTool().equals("customerHistory"))
                .singleElement().satisfies(s -> {
                    assertThat(s.getStatus()).isEqualTo(InvestigationStep.Status.ERROR);
                    assertThat(s.getOutput()).contains("tx-ingest is not reachable");
                });
        verify(noteWriter).write(evidence.capture());
        assertThat(evidence.getValue().describe()).contains("NOT ESTABLISHED", "transaction history could not be read");
        assertThat(investigation.getStatus()).isEqualTo(Investigation.Status.DRAFTED);
    }

    @Test
    void anUnreadableAlertFailsTheRun() {
        start(AgentProperties.Planner.FIXED);
        when(tools.riskScore(ALERT)).thenThrow(new ServiceUnavailableException("scoring-service", null));

        runner(6).run(investigation.getId());

        assertThat(investigation.getStatus()).isEqualTo(Investigation.Status.FAILED);
        verify(noteWriter, never()).write(any());
    }

    @Test
    void whenTheModelPlannerCallsNoToolsThePlanFillsTheGapsAndSaysSo() {
        start(AgentProperties.Planner.LLM);
        ChatResponse noToolCalls = mock(ChatResponse.class);
        when(noToolCalls.hasToolCalls()).thenReturn(false);
        when(chatModel.call(any(Prompt.class))).thenReturn(noToolCalls);

        runner(6).run(investigation.getId());

        assertThat(recordedSteps()).extracting(InvestigationStep::getTool).containsExactly(
                "riskScore", "customerHistory", "peerSegment", "policyLookup", "draftCaseNote");
        assertThat(recordedSteps().get(1).getInput()).contains("model did not ask");
    }

    @Test
    void whenTheModelIsDownThePlanTakesOverAndTheGapIsRecorded() {
        start(AgentProperties.Planner.LLM);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("connection refused"));
        ArgumentCaptor<Evidence> evidence = ArgumentCaptor.forClass(Evidence.class);

        runner(6).run(investigation.getId());

        verify(noteWriter).write(evidence.capture());
        assertThat(evidence.getValue().gaps).anyMatch(g -> g.contains("model planner was unavailable"));
        assertThat(evidence.getValue().history).isNotNull();
        assertThat(investigation.getStatus()).isEqualTo(Investigation.Status.DRAFTED);
    }

    @Test
    void theFixedPlansPolicyQuestionFollowsTheFindings() {
        assertThat(InvestigationTools.policyQuestionFor(ALERT_SNAPSHOT)).contains("structuring");
    }

    @Test
    void policyFromSeveralLookupsIsNumberedOnceAndInOrder() {
        Evidence evidence = new Evidence();
        UUID doc = UUID.randomUUID();
        evidence.addPolicy(List.of(new Citation(1, doc, "Policy", 4, 0.9, "a"), new Citation(2, doc, "Policy", 5, 0.8, "b")));
        evidence.addPolicy(List.of(new Citation(1, doc, "Policy", 5, 0.8, "b"), new Citation(2, doc, "Policy", 6, 0.7, "c")));

        assertThat(evidence.policy).extracting(Citation::marker, Citation::chunkIndex).containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, 4), org.assertj.core.groups.Tuple.tuple(2, 5),
                org.assertj.core.groups.Tuple.tuple(3, 6));
    }

    @Test
    void theEvidenceNamesTheClausesEachPolicyExcerptContains() {
        Evidence evidence = new Evidence();
        evidence.addPolicy(List.of(new Citation(1, UUID.randomUUID(), "Internal AML Policy", 1, 0.8,
                "AML-04  Structuring\nAML-04.1  Structuring is ...\nAML-04.2  Where three or more ...")));

        assertThat(evidence.describe()).contains("[1] Internal AML Policy (clauses AML-04.1, AML-04.2)");
    }

    @Test
    void aModelThatIsNotLocalNeverSeesTheEvidence() {
        AgentProperties properties = new AgentProperties(AgentProperties.Planner.FIXED, 6, "qwen2.5:3b",
                "http://scoring", "http://ingest", 20);
        CaseNoteWriter writer = new CaseNoteWriter(mock(org.springframework.ai.chat.client.ChatClient.class),
                chatModel, properties);
        Evidence evidence = new Evidence();
        evidence.alert = ALERT_SNAPSHOT;

        CaseNoteWriter.CaseNote note = writer.write(evidence);

        assertThat(writer.modelIsLocal()).isFalse();
        assertThat(note.source()).isEqualTo(Investigation.NoteSource.TEMPLATE);
        assertThat(note.text()).contains("not local", "C-20001");
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void theTraceShowsWhoAskedForEachStep() {
        start(AgentProperties.Planner.FIXED);

        runner(6).run(investigation.getId());

        assertThat(recordedSteps().get(1).getInput()).contains("\"requestedBy\":\"plan\"", "C-20001");
    }
}
