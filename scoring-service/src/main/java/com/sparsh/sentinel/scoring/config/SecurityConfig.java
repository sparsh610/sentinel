package com.sparsh.sentinel.scoring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call scoring-service. Every request needs a Keycloak token for the sentinel-api
 * audience (application.yml); the roles come from its "roles" claim.
 *
 * <p>People read the alert queue. Only copilot-service, with its own service account, moves an
 * alert's status: it does so when a person decides a case there, and that decision is recorded
 * against the person. An analyst's token cannot close an alert behind the investigation's back.
 */
@Configuration
public class SecurityConfig {

    static final String ANALYST = "ANALYST";
    static final String SENIOR_APPROVER = "SENIOR_APPROVER";
    static final String SERVICE = "SERVICE";

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Where Spring forwards a 4xx/5xx to render it: the original request was
                        // already judged, so the forward must not be judged again.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/alerts", "/api/alerts/*",
                                "/api/transactions/*/peer-segment").hasAnyRole(ANALYST, SENIOR_APPROVER, SERVICE)
                        .requestMatchers(HttpMethod.PATCH, "/api/alerts/*/status").hasRole(SERVICE)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                // Bearer tokens, no cookies: nothing for a cross-site request to ride on.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
