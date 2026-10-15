package com.sparsh.sentinel.copilot.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.web.SecurityFilterChain;

import static com.sparsh.sentinel.copilot.security.CurrentUser.ANALYST;
import static com.sparsh.sentinel.copilot.security.CurrentUser.SENIOR_APPROVER;

/**
 * Who may call copilot-service, and how it calls the other two services.
 *
 * <p>The URL rules here are the outer fence. The rules that matter are also enforced where the
 * work is done - {@code @PreAuthorize} on the decisions and the upload, the document ACL inside
 * retrieval - because the MCP endpoint reaches the same code without passing these URLs
 * (design decision §8).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** The client registration in application.yml that copilot-service's own calls use. */
    static final String SERVICE_REGISTRATION = "copilot";

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/investigations/*/decision").hasRole(ANALYST)
                        .requestMatchers(HttpMethod.POST, "/api/investigations/*/sign-off").hasRole(SENIOR_APPROVER)
                        .requestMatchers(HttpMethod.POST, "/api/documents").hasRole(SENIOR_APPROVER)
                        .requestMatchers("/api/audit/**", "/api/audit").hasRole(SENIOR_APPROVER)
                        .requestMatchers("/api/**", "/mcp", "/mcp/**").hasAnyRole(ANALYST, SENIOR_APPROVER)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                // Bearer tokens, no cookies: nothing for a cross-site request to ride on.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /**
     * Fetches and caches copilot-service's own token (client credentials) for the calls to
     * scoring-service and tx-ingest. Not tied to a request, so it works on the agent's thread.
     */
    @Bean
    OAuth2AuthorizedClientManager serviceClientManager(ClientRegistrationRepository registrations,
                                                       OAuth2AuthorizedClientService clients) {
        return new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
    }

    /**
     * Adds that token to a RestClient's requests. Given only to ScoringClient and IngestClient -
     * not set on every RestClient, or Spring AI's calls to Ollama would carry it too.
     */
    @Bean
    OAuth2ClientHttpRequestInterceptor serviceTokenInterceptor(OAuth2AuthorizedClientManager serviceClientManager) {
        OAuth2ClientHttpRequestInterceptor interceptor = new OAuth2ClientHttpRequestInterceptor(serviceClientManager);
        interceptor.setClientRegistrationIdResolver(request -> SERVICE_REGISTRATION);
        // The token is the service's, whoever is on the thread, so it is cached under one name.
        Authentication service = new AnonymousAuthenticationToken("copilot-service", "copilot-service",
                AuthorityUtils.createAuthorityList("ROLE_SERVICE"));
        interceptor.setPrincipalResolver(request -> service);
        return interceptor;
    }
}
