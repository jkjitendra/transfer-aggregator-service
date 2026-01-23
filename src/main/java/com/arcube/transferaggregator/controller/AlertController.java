package com.arcube.transferaggregator.controller;

import com.arcube.transferaggregator.observability.ErrorMetrics;
import com.arcube.transferaggregator.service.AlertingService;
import com.arcube.transferaggregator.service.AlertingService.AlertStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
            "totalDlqSize", errorMetrics.getTotalDlqSize()
        ));
    }

    /**
     * Simulate adding items to DLQ (for testing).
     * POST /api/v1/admin/alerts/dlq/simulate?count=5
     */
    @PostMapping("/dlq/simulate")
    public ResponseEntity<Map<String, Object>> simulateDlqItems(
            @RequestParam(defaultValue = "1") int count) {
        log.info("Simulating {} DLQ items", count);
        for (int i = 0; i < count; i++) {
            errorMetrics.incrementDlqSize("main");
        }
        return ResponseEntity.ok(Map.of(
            "added", count,
            "newSize", errorMetrics.getDlqSize("main")
        ));
    }

    /**
     * Clear DLQ (simulate processing).
     * DELETE /api/v1/admin/alerts/dlq
     */
    @DeleteMapping("/dlq")
    public ResponseEntity<Map<String, Object>> clearDlq() {
        int current = errorMetrics.getDlqSize("main");
        for (int i = 0; i < current; i++) {
            errorMetrics.decrementDlqSize("main");
        }
        log.info("DLQ cleared: {} items removed", current);
        return ResponseEntity.ok(Map.of(
            "cleared", current,
            "newSize", errorMetrics.getDlqSize("main")
        ));
    }

    /**
     * Manually trigger alerting check.
     * POST /api/v1/admin/alerts/check
     */
    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> triggerCheck() {
        log.info("Manually triggering alerting check");
        alertingService.checkAlerts();
        return ResponseEntity.ok(Map.of(
            "status", "check completed",
            "dlqStatus", alertingService.getDlqStatus()
        ));
    }
}
