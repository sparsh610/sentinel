package com.sparsh.sentinel.copilot.agent;

import com.sparsh.sentinel.copilot.agent.client.ScoringClient;
import com.sparsh.sentinel.copilot.audit.AuditEvent.Channel;
import com.sparsh.sentinel.copilot.audit.AuditEvent.Outcome;
import com.sparsh.sentinel.copilot.audit.AuditLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.sparsh.sentinel.copilot.agent.InvestigationDecision.Action.APPROVE;
import static com.sparsh.sentinel.copilot.agent.InvestigationDecision.Action.ESCALATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What the audit log keeps about people's decisions - including the ones that were refused. */
@ExtendWith(MockitoExtension.class)
class AuditTrailTest {

    @Mock private InvestigationRepository investigations;
    @Mock private ScoringClient scoring;
    @Mock private InvestigationRunner runner;
    @Mock private TaskExecutor executor;
    @Mock private CaseNoteWriter noteWriter;
    @Mock private AgentProperties properties;
    @Mock private AuditLog audit;

    private InvestigationService service() {
        return new InvestigationService(investigations, scoring, runner, executor, noteWriter, properties, audit,
                Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC));
    }

    private static void signedInAs(String name, String... roles) {
        String[] authorities = java.util.Arrays.stream(roles).map(r -> "ROLE_" + r).toArray(String[]::new);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(name, null, authorities));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aDecisionIsRecordedInTheNameOnTheToken() {
        Investigation drafted = InvestigationLifecycleTest.drafted();
        when(investigations.findById(drafted.getId())).thenReturn(Optional.of(drafted));
        signedInAs("alex.analyst", "ANALYST");

        service().analystDecides(drafted.getId(), ESCALATE, "Three deposits under the threshold");

        assertThat(drafted.getDecisions()).singleElement()
                .satisfies(d -> assertThat(d.getActor()).isEqualTo("alex.analyst"));
        verify(audit).record(eq(Channel.API), eq("DECISION"), eq(drafted.getId().toString()), eq(Outcome.OK),
                contains("ESCALATE"));
    }

    /** Four eyes, by the name on the token: the refusal itself is kept, not only the 409. */
    @Test
    void signingOffYourOwnEscalationIsRefusedAndLogged() {
        Investigation drafted = InvestigationLifecycleTest.drafted();
        when(investigations.findById(drafted.getId())).thenReturn(Optional.of(drafted));
        signedInAs("sam.senior", "ANALYST", "SENIOR_APPROVER");
        service().analystDecides(drafted.getId(), ESCALATE, null);

        assertThatThrownBy(() -> service().approverDecides(drafted.getId(), APPROVE, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot also sign it off");

        verify(audit).refused(eq(Channel.API), eq("SIGN_OFF"), eq(drafted.getId().toString()), eq(Outcome.DENIED),
                contains("cannot also sign it off"));
        assertThat(drafted.getStatus()).isEqualTo(Investigation.Status.AWAITING_SIGN_OFF);
    }
}
