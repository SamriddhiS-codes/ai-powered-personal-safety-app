package com.ascenddevs.safetyapp.controller;

import com.ascenddevs.safetyapp.dto.AlertRequest;
import com.ascenddevs.safetyapp.dto.AlertResponse;
import com.ascenddevs.safetyapp.dto.VerifyResponse;
import com.ascenddevs.safetyapp.entity.Alert;
import com.ascenddevs.safetyapp.repository.AlertRepository;
import com.ascenddevs.safetyapp.security.AlertTamper;
import com.ascenddevs.safetyapp.service.AlertService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;
    private final AlertRepository alertRepository;

    public AlertController(AlertService alertService, AlertRepository alertRepository) {
        this.alertService = alertService;
        this.alertRepository = alertRepository;
    }

    @PostMapping
    public ResponseEntity<AlertResponse> raiseAlert(@RequestBody AlertRequest request) {
        Alert alert = alertService.raiseAlert(request);
        return ResponseEntity.ok(AlertResponse.from(alert));
    }

    @GetMapping
    public ResponseEntity<List<AlertResponse>> listAlerts(
            @RequestParam(required = false) String status) {
        List<Alert> alerts = (status == null)
                ? alertRepository.findAll()
                : alertRepository.findByVerificationStatus(Alert.VerificationStatus.valueOf(status));
        return ResponseEntity.ok(alerts.stream().map(AlertResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertResponse> getAlert(@PathVariable UUID id) {
        return alertRepository.findById(id)
                .map(alert -> ResponseEntity.ok(AlertResponse.from(alert)))
                .orElseThrow(() -> new NoSuchElementException("Alert not found: " + id));
    }

    /**
     * Re-verifies a stored alert (signature, timestamp, nonce). The first call returns VERIFIED
     * and uses up the nonce; calling again returns REJECTED_REPLAY.
     * ?tamper=payload|signature|timestamp|stale simulates a modified alert, for demos and tests.
     */
    @PostMapping("/{id}/verify")
    public ResponseEntity<VerifyResponse> verify(@PathVariable UUID id,
                                                 @RequestParam(defaultValue = "none") String tamper) {
        AlertTamper.Mode mode;
        try {
            mode = AlertTamper.Mode.parse(tamper);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return ResponseEntity.ok(VerifyResponse.of(id, mode, alertService.verifyStored(id, mode)));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Void> notFound() {
        return ResponseEntity.notFound().build();
    }
}
