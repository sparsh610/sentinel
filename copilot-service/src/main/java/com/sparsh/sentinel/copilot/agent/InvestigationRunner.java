package com.sparsh.sentinel.copilot.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Works one investigation from start to draft, on a background thread: read the alert, let the
 * planner gather evidence, draft the note. Each step is committed as it finishes, so the console
 * can show the trace growing while the run is still going.
 */
@Component
public class InvestigationRunner {

    private static final Logger log = LoggerFactory.getLogger(InvestigationRunner.class);

    private final InvestigationRepository investigations;
    private final InvestigationStepRepository steps;
    private final InvestigationTools tools;
    private final FixedPlanner fixedPlanner;
    private final LlmPlanner llmPlanner;
    private final CaseNoteWriter noteWriter;
    private final AgentProperties properties;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public InvestigationRunner(InvestigationRepository investigations, InvestigationStepRepository steps,
                               InvestigationTools tools, FixedPlanner fixedPlanner, LlmPlanner llmPlanner,
                               CaseNoteWriter noteWriter, AgentProperties properties, ObjectMapper json,
                               PlatformTransactionManager transactionManager, Clock clock) {
        this.investigations = investigations;
        this.steps = steps;
        this.tools = tools;
        this.fixedPlanner = fixedPlanner;
        this.llmPlanner = llmPlanner;
        this.noteWriter = noteWriter;
        this.properties = properties;
        this.json = json;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public void run(UUID investigationId) {
        Investigation investigation = investigations.findById(investigationId).orElseThrow();
        StepRecorder recorder = new StepRecorder(investigation, properties.maxSteps(), steps, json, clock);
        Evidence evidence = new Evidence();
        try {
            evidence.alert = recorder.run(InvestigationTools.RISK_SCORE,
                            Map.of("alertId", investigation.getAlertId(), "requestedBy", FixedPlanner.BY_PLAN),
                            () -> tools.riskScore(investigation.getAlertId()))
                    .orElse(null);
            if (evidence.alert == null) {
                finish(investigationId, i -> i.failed("The alert could not be read from scoring-service", clock.instant()));
                return;
            }

            if (investigation.getPlanner() == AgentProperties.Planner.LLM) {
                llmPlanner.gather(evidence, recorder);
            } else {
                fixedPlanner.gather(evidence, recorder);
            }

            String writer = noteWriter.modelIsLocal() ? properties.model() : "template";
            CaseNoteWriter.CaseNote note = recorder.run(StepRecorder.DRAFT_CASE_NOTE, Map.of("writer", writer),
                            () -> noteWriter.write(evidence))
                    .orElseGet(() -> CaseNoteWriter.template(evidence, "the note step failed"));
            finish(investigationId, i -> i.drafted(note.text(), note.source(), clock.instant()));
            log.info("Investigation {} drafted in {} steps ({} note)", investigationId, recorder.used(), note.source());
        } catch (RuntimeException e) {
            log.error("Investigation {} failed", investigationId, e);
            finish(investigationId, i -> i.failed(e.getClass().getSimpleName() + ": " + e.getMessage(), clock.instant()));
        }
    }

    private void finish(UUID investigationId, java.util.function.Consumer<Investigation> change) {
        transaction.executeWithoutResult(status ->
                investigations.findById(investigationId).ifPresent(change));
    }
}
