package com.sparsh.sentinel.copilot.audit;

import com.sparsh.sentinel.copilot.audit.AuditEvent.Channel;
import com.sparsh.sentinel.copilot.audit.AuditEvent.Outcome;
import com.sparsh.sentinel.copilot.privacy.PiiMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Writes the audit log.
 *
 * <p>{@link #record} joins the caller's transaction: a decision and its audit line commit
 * together or not at all, so the log never claims something happened that was rolled back.
 * {@link #refused} writes in a transaction of its own, because a refusal is usually followed by
 * the caller's rollback - and an attempt to sign off one's own escalation is exactly the line the
 * log must keep.
 *
 * <p>IBANs, card numbers and e-mail addresses in {@code detail} - an analyst's comment, a policy
 * question - are masked before the line is written; the log is read by people who are not all
 * entitled to see them.
 */
@Service
public class AuditLog {

    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);
    private static final int MAX_DETAIL = 2_000;

    private final AuditEventRepository events;
    private final PiiMasker masker;
    private final Clock clock;

    public AuditLog(AuditEventRepository events, PiiMasker masker, Clock clock) {
        this.events = events;
        this.masker = masker;
        this.clock = clock;
    }

    /** An action by the person on the current request. */
    @Transactional
    public void record(Channel channel, String action, String target, Outcome outcome, String detail) {
        recordAs(currentActor(), channel, action, target, outcome, detail);
    }

    /** An action by a named actor - the agent's background thread has no request to ask. */
    @Transactional
    public void recordAs(String actor, Channel channel, String action, String target, Outcome outcome, String detail) {
        events.save(new AuditEvent(clock.instant(), actor, channel, action, cap(target, 128), outcome,
                cap(detail == null ? null : masker.maskPatterns(detail), MAX_DETAIL)));
        log.info("AUDIT {} {} {} {} {}", actor, channel, action, target, outcome);
    }

    /** A refused or failed attempt, kept even when the caller's transaction rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refused(Channel channel, String action, String target, Outcome outcome, String detail) {
        recordAs(currentActor(), channel, action, target, outcome, detail);
    }

    static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getName() == null ? "anonymous" : auth.getName();
    }

    private static String cap(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
