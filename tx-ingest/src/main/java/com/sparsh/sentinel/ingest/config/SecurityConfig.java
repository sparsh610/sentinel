package com.sparsh.sentinel.ingest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call tx-ingest. Every request needs a Keycloak token for the sentinel-api audience
 * (application.yml); the roles come from its "roles" claim.
 *
 * <p>Booking a transaction is for source systems (the INGEST role, a service account), not for
 * people. Reading a customer's history is for the people investigating and for copilot-service's
 * agent. The traffic simulator is a demo control in the console, so any investigator may run it.
 */
@Configuration
public class SecurityConfig {

    static final String ANALYST = "ANALYST";
    static final String SENIOR_APPROVER = "SENIOR_APPROVER";
    static final String SERVICE = "SERVICE";
    static final String INGEST = "INGEST";

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Where Spring forwards a 4xx/5xx to render it: the original request was
                        // already judged, so the forward must not be judged again.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/transactions").hasRole(INGEST)
                        .requestMatchers(HttpMethod.GET, "/api/customers/*/transactions")
                        .hasAnyRole(ANALYST, SENIOR_APPROVER, SERVICE)
                        .requestMatchers(HttpMethod.POST, "/api/simulations").hasAnyRole(ANALYST, SENIOR_APPROVER)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                // Bearer tokens, no cookies: nothing for a cross-site request to ride on.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
