package com.sparsh.sentinel.copilot.security;

import com.sparsh.sentinel.copilot.agent.InvestigationController;
import com.sparsh.sentinel.copilot.agent.InvestigationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The outer fence: which role reaches which investigation endpoint. */
@WebMvcTest(InvestigationController.class)
@Import(SecurityConfig.class)
class EndpointRulesTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private InvestigationService service;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private ClientRegistrationRepository registrations;
    @MockitoBean private OAuth2AuthorizedClientService authorizedClients;

    private final String investigation = "/api/investigations/" + UUID.randomUUID();

    private static SimpleGrantedAuthority role(String name) {
        return new SimpleGrantedAuthority("ROLE_" + name);
    }

    private static MockHttpServletRequestBuilder decide(String path, String action) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"" + action + "\"}");
    }

    @Test
    void nothingWithoutAToken() throws Exception {
        mvc.perform(get(investigation)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/investigations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"alertId\":\"" + UUID.randomUUID() + "\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAnalystDecidesButCannotSignOff() throws Exception {
        mvc.perform(decide(investigation + "/decision", "ESCALATE").with(jwt().authorities(role("ANALYST"))))
                .andExpect(status().isOk());
        mvc.perform(decide(investigation + "/sign-off", "APPROVE").with(jwt().authorities(role("ANALYST"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSeniorApproverSignsOff() throws Exception {
        mvc.perform(decide(investigation + "/sign-off", "APPROVE").with(jwt().authorities(role("SENIOR_APPROVER"))))
                .andExpect(status().isOk());
    }

    /** The service account reads and moves alerts in scoring-service; it has no business here. */
    @Test
    void theServiceAccountCannotWorkCases() throws Exception {
        mvc.perform(get(investigation).with(jwt().authorities(role("SERVICE")))).andExpect(status().isForbidden());
    }

    @Test
    void theRequestBodyNoLongerSaysWhoIsDeciding() throws Exception {
        mvc.perform(post(investigation + "/decision").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CLOSE\",\"actor\":\"someone-else\"}")
                        .with(jwt().authorities(role("ANALYST"))))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(service).analystDecides(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(com.sparsh.sentinel.copilot.agent.InvestigationDecision.Action.CLOSE),
                org.mockito.ArgumentMatchers.isNull());
    }
}
