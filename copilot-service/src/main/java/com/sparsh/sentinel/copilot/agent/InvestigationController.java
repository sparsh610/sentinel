package com.sparsh.sentinel.copilot.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Investigations of alerts. Starting one returns 202 at once - the agent runs in the background
 * and the console polls {@code GET /{id}} to watch the trace grow.
 *
 * <p>Who decides is taken from the request for now. Week 7 replaces it with the authenticated
 * user and checks the SENIOR_APPROVER role on sign-off; the four-eyes rule already holds.
 */
@RestController
@RequestMapping("/api/investigations")
public class InvestigationController {

    private final InvestigationService service;

    public InvestigationController(InvestigationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public InvestigationView start(@RequestBody @Valid StartRequest request) {
        return service.start(request.alertId());
    }

    @GetMapping("/{id}")
    public InvestigationView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping
    public List<InvestigationView> forAlert(@RequestParam UUID alertId) {
        return service.forAlert(alertId);
    }

    /** The analyst: ESCALATE (for sign-off) or CLOSE. */
    @PostMapping("/{id}/decision")
    public InvestigationView decide(@PathVariable UUID id, @RequestBody @Valid DecisionRequest request) {
        return service.analystDecides(id, request.action(), request.actor(), request.comment());
    }

    /** The senior approver: APPROVE (report it) or RETURN (back to the analyst). */
    @PostMapping("/{id}/sign-off")
    public InvestigationView signOff(@PathVariable UUID id, @RequestBody @Valid DecisionRequest request) {
        return service.approverDecides(id, request.action(), request.actor(), request.comment());
    }

    public record StartRequest(@NotNull UUID alertId) {
    }

    public record DecisionRequest(@NotNull InvestigationDecision.Action action,
                                  @NotBlank @Size(max = 64) String actor,
                                  @Size(max = 2000) String comment) {
    }
}
