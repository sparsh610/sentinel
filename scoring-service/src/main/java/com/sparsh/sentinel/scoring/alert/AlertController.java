package com.sparsh.sentinel.scoring.alert;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Read side of the alert queue. Status changes arrive with the approval flow in weeks 5-6, and
 * they will not be here: an alert is worked as a case in copilot-service.
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
}
