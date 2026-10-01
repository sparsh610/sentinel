package com.sparsh.sentinel.copilot.agent;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.sparsh.sentinel.copilot.agent.InvestigationDecision.Action.APPROVE;
import static com.sparsh.sentinel.copilot.agent.InvestigationDecision.Action.CLOSE;
import static com.sparsh.sentinel.copilot.agent.InvestigationDecision.Action.ESCALATE;
import static com.sparsh.sentinel.copilot.agent.InvestigationDecision.Action.RETURN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvestigationLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    static Investigation drafted() {
        Investigation investigation = Investigation.start(UUID.randomUUID(), UUID.randomUUID(), "C-10001",
                new BigDecimal("9500"), "EUR", AgentProperties.Planner.FIXED, NOW);
        investigation.drafted("note", Investigation.NoteSource.TEMPLATE, NOW);
        return investigation;
    }

    @Test
    void theAgentCanOnlyDraftPeopleDecide() {
        Investigation investigation = Investigation.start(UUID.randomUUID(), UUID.randomUUID(), "C-10001",
                new BigDecimal("9500"), "EUR", AgentProperties.Planner.FIXED, NOW);

        assertThatThrownBy(() -> investigation.analystDecides(ESCALATE, "ana", null, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anEscalationIsReportedOnlyAfterASecondPersonSignsItOff() {
        Investigation investigation = drafted();

        investigation.analystDecides(ESCALATE, "ana", "Three deposits under the threshold", NOW);
        assertThat(investigation.getStatus()).isEqualTo(Investigation.Status.AWAITING_SIGN_OFF);

        investigation.approverDecides(APPROVE, "sam", "Agreed", NOW);
        assertThat(investigation.getStatus()).isEqualTo(Investigation.Status.REPORTED);
        assertThat(investigation.getDecisions()).extracting(InvestigationDecision::getActor).containsExactly("ana", "sam");
    }

    @Test
    void theAnalystWhoEscalatedCannotSignItOff() {
        Investigation investigation = drafted();
        investigation.analystDecides(ESCALATE, "ana", null, NOW);

        assertThatThrownBy(() -> investigation.approverDecides(APPROVE, " ANA ", null, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot also sign it off");
        assertThat(investigation.getStatus()).isEqualTo(Investigation.Status.AWAITING_SIGN_OFF);
    }

    @Test
    void aReturnedCaseGoesBackToTheAnalyst() {
        Investigation investigation = drafted();
        investigation.analystDecides(ESCALATE, "ana", null, NOW);

        investigation.approverDecides(RETURN, "sam", "Check the counterparty first", NOW);

        assertThat(investigation.getStatus()).isEqualTo(Investigation.Status.DRAFTED);
    }

    @Test
    void theAnalystCanCloseAlone() {
        Investigation investigation = drafted();

        investigation.analystDecides(CLOSE, "ana", "Known supplier", NOW);

        assertThat(investigation.getStatus()).isEqualTo(Investigation.Status.CLOSED);
        assertThat(investigation.isActive()).isFalse();
    }

    @Test
    void rolesCannotUseEachOthersActions() {
        Investigation investigation = drafted();

        assertThatThrownBy(() -> investigation.analystDecides(APPROVE, "ana", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDecidedCaseCannotBeDecidedAgain() {
        Investigation investigation = drafted();
        investigation.analystDecides(CLOSE, "ana", null, NOW);

        assertThatThrownBy(() -> investigation.analystDecides(ESCALATE, "ana", null, NOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
