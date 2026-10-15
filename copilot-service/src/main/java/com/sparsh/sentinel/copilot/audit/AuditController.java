package com.sparsh.sentinel.copilot.audit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** The audit log, newest first. Senior approvers only (SecurityConfig). */
@RestController
@RequestMapping("/api/audit")
@Validated
public class AuditController {

    private final AuditEventRepository events;

    public AuditController(AuditEventRepository events) {
        this.events = events;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AuditView> search(@RequestParam(required = false) String actor,
                                  @RequestParam(required = false) String action,
                                  @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return events.search(blankToNull(actor), blankToNull(action), PageRequest.of(0, limit)).stream()
                .map(AuditView::of)
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public record AuditView(long id, Instant occurredAt, String actor, AuditEvent.Channel channel, String action,
                            String target, AuditEvent.Outcome outcome, String detail) {

        static AuditView of(AuditEvent e) {
            return new AuditView(e.getId(), e.getOccurredAt(), e.getActor(), e.getChannel(), e.getAction(),
                    e.getTarget(), e.getOutcome(), e.getDetail());
        }
    }
}
