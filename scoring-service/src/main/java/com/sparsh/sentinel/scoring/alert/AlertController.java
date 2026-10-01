package com.sparsh.sentinel.scoring.alert;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * The alert queue, and the alert's status. An alert is worked as a case in copilot-service,
 * which moves its status here as the investigation starts and as people decide it. The rules for
 * which moves are allowed live on {@link Alert}, so no caller can skip them.
 */
@Validated
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertRepository alerts;

    public AlertController(AlertRepository alerts) {
        this.alerts = alerts;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AlertView> queue(@RequestParam(defaultValue = "OPEN") Alert.Status status,
                                 @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return alerts.findByStatusOrderByScoreDescRaisedAtDesc(status, PageRequest.of(0, limit))
                .stream()
                .map(AlertView::of)
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public AlertView one(@PathVariable UUID id) {
        return alerts.findById(id)
                .map(AlertView::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No alert " + id));
    }

    /** 409 for a move the lifecycle does not allow, such as reopening a closed alert. */
    @PatchMapping("/{id}/status")
    @Transactional
    public AlertView changeStatus(@PathVariable UUID id, @RequestBody @Valid StatusChange change) {
        Alert alert = alerts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No alert " + id));
        try {
            alert.moveTo(change.status());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        return AlertView.of(alert);
    }

    public record StatusChange(@NotNull Alert.Status status) {
    }
}
