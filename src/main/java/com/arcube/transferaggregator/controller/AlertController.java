package com.arcube.transferaggregator.controller;

import com.arcube.transferaggregator.observability.ErrorMetrics;
import com.arcube.transferaggregator.service.AlertingService;
import com.arcube.transferaggregator.service.AlertingService.AlertStatus;
import com.arcube.transferaggregator.workflow.CancellationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Admin endpoints for alerting and metrics monitoring.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertingService alertingService;
    private final ErrorMetrics errorMetrics;
    private final Optional<CancellationRepository> durableCancellations;

    /**
     * Get current DLQ status.
     * GET /api/v1/admin/alerts/dlq
     */
    @GetMapping("/dlq")
    public ResponseEntity<AlertStatus> getDlqStatus() {
        return ResponseEntity.ok(alertingService.getDlqStatus());
    }

    /**
     * Get current error metrics summary.
     * GET /api/v1/admin/alerts/errors
     */
    @GetMapping("/errors")
    public ResponseEntity<Map<String, Object>> getErrorMetrics() {
        return ResponseEntity.ok(Map.of(
            "searchErrors", errorMetrics.getSearchErrorCount(),
            "bookingErrors", errorMetrics.getBookingErrorCount(),
            "cancellationErrors", errorMetrics.getCancellationErrorCount(),
            "totalDlqSize", durableCancellations.map(CancellationRepository::activeDeadLetterCount)
                .orElseGet(errorMetrics::getTotalDlqSize)
        ));
    }

    /**
     * Manually trigger alerting check.
     * POST /api/v1/admin/alerts/check
     */
    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> triggerCheck() {
        Authentication actor = SecurityContextHolder.getContext().getAuthentication();
        log.info("AUDIT action=alert_check actor={}", actor == null ? "system" : actor.getName());
        alertingService.checkAlerts();
        return ResponseEntity.ok(Map.of(
            "status", "check completed",
            "dlqStatus", alertingService.getDlqStatus()
        ));
    }
}
