package com.sparsh.sentinel.copilot.security;

import com.sparsh.sentinel.copilot.document.Classification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * The person behind the current request, as the Keycloak token names them.
 *
 * <p>Read on the request thread and carried from there - the agent's background run has no
 * request, so it works with the identity and clearance the investigation was started with.
 *
 * @param name  the token's preferred_username, e.g. "alex.analyst"
 * @param roles the realm roles, without the ROLE_ prefix
 */
public record CurrentUser(String name, Set<String> roles) {

    public static final String ANALYST = "ANALYST";
    public static final String SENIOR_APPROVER = "SENIOR_APPROVER";

    /** The caller of the current request; fails if there is none, rather than guessing one. */
    public static CurrentUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new IllegalStateException("No authenticated user on this thread");
        }
        Set<String> roles = new TreeSet<>();
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String name = authority.getAuthority();
            if (name != null && name.startsWith("ROLE_")) {
                roles.add(name.substring("ROLE_".length()));
            }
        }
        return new CurrentUser(auth.getName(), Set.copyOf(roles));
    }

    public boolean has(String role) {
        return roles.contains(role);
    }

    /** Which policy documents this person may be shown: the document ACL, applied at retrieval. */
    public Set<Classification> clearance() {
        return clearanceFor(roles);
    }

    /**
     * PUBLIC for anyone signed in, INTERNAL for investigators, CONFIDENTIAL for senior approvers
     * only - the rule {@link Classification} describes.
     */
    public static Set<Classification> clearanceFor(Set<String> roles) {
        Set<Classification> allowed = EnumSet.of(Classification.PUBLIC);
        if (roles.contains(ANALYST) || roles.contains(SENIOR_APPROVER)) {
            allowed.add(Classification.INTERNAL);
        }
        if (roles.contains(SENIOR_APPROVER)) {
            allowed.add(Classification.CONFIDENTIAL);
        }
        return allowed;
    }
}
