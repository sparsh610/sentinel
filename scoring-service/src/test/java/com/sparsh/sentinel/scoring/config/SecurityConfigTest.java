package com.sparsh.sentinel.scoring.config;

import com.sparsh.sentinel.scoring.alert.AlertController;
import com.sparsh.sentinel.scoring.alert.AlertRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may reach the alert API. A 404 means the caller got through to the controller (the alert
 * repository is empty); a 401 or 403 means security stopped them first.
 */
@WebMvcTest(AlertController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private AlertRepository alerts;
    @MockitoBean private PlatformTransactionManager transactionManager;
    @MockitoBean private JwtDecoder jwtDecoder;

    private final String alert = "/api/alerts/" + UUID.randomUUID();

    private static MockHttpServletRequestBuilder close(String path) {
        return patch(path + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}");
    }

    @Test
    void noTokenNoAlerts() throws Exception {
        mvc.perform(get("/api/alerts")).andExpect(status().isUnauthorized());
    }

    @Test
    void investigatorsAndTheCopilotServiceReadTheQueue() throws Exception {
        mvc.perform(get(alert).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST"))))
                .andExpect(status().isNotFound());
        mvc.perform(get(alert).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SERVICE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void aPersonCannotMoveAnAlertBehindTheInvestigationsBack() throws Exception {
        mvc.perform(close(alert).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SENIOR_APPROVER"))))
                .andExpect(status().isForbidden());
        mvc.perform(close(alert).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SERVICE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void aTokenWithoutARoleIsRefused() throws Exception {
        mvc.perform(get(alert).with(jwt())).andExpect(status().isForbidden());
    }
}
