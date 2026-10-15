package com.sparsh.sentinel.copilot.security;

import com.sparsh.sentinel.copilot.agent.AgentProperties;
import com.sparsh.sentinel.copilot.agent.CaseNoteWriter;
import com.sparsh.sentinel.copilot.agent.InvestigationDecision;
import com.sparsh.sentinel.copilot.agent.InvestigationRepository;
import com.sparsh.sentinel.copilot.agent.InvestigationRunner;
import com.sparsh.sentinel.copilot.agent.InvestigationService;
import com.sparsh.sentinel.copilot.agent.client.ScoringClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The role checks on the service methods themselves - the ones that also hold for the MCP
 * endpoint, which reaches {@link InvestigationService} without passing the REST URLs.
 */
@SpringJUnitConfig(MethodRulesTest.Config.class)
class MethodRulesTest {

    @Configuration
    @EnableMethodSecurity
    @Import(InvestigationService.class)
    static class Config {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @MockitoBean private InvestigationRepository investigations;
    @MockitoBean private ScoringClient scoring;
    @MockitoBean private InvestigationRunner runner;
    @MockitoBean private TaskExecutor executor;
    @MockitoBean private CaseNoteWriter noteWriter;
    @MockitoBean private AgentProperties properties;

    @Autowired private InvestigationService service;

    @Test
    @WithMockUser(username = "alex.analyst", roles = "ANALYST")
    void anAnalystCannotSignOffEvenWithoutTheUrl() {
        assertThatThrownBy(() -> service.approverDecides(UUID.randomUUID(), InvestigationDecision.Action.APPROVE, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "sam.senior", roles = "SENIOR_APPROVER")
    void aSeniorApproverWhoIsNotAnAnalystCannotTakeTheAnalystsDecision() {
        assertThatThrownBy(() -> service.analystDecides(UUID.randomUUID(), InvestigationDecision.Action.CLOSE, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "copilot", roles = "SERVICE")
    void theServiceAccountCannotStartAnInvestigation() {
        assertThatThrownBy(() -> service.start(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
    }
}
