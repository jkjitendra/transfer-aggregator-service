package com.arcube.transferaggregator.controller;

import com.arcube.transferaggregator.observability.ErrorMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Demonstration-only counter controls; never registered outside the demo profile. */
@RestController
@RequestMapping("/api/v1/admin/alerts/dlq")
@Profile("demo & !prod")
@RequiredArgsConstructor
public class AlertSimulationController {
    private final ErrorMetrics errorMetrics;

    @PostMapping("/simulate")
    public ResponseEntity<Map<String, Object>> simulate(@RequestParam(defaultValue = "1") int count) {
        if (count < 1 || count > 100) return ResponseEntity.badRequest().build();
        for (int i = 0; i < count; i++) errorMetrics.incrementDlqSize("main");
        return ResponseEntity.ok(Map.of("added", count, "newSize", errorMetrics.getDlqSize("main")));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clear() {
        int current = errorMetrics.getDlqSize("main");
        for (int i = 0; i < current; i++) errorMetrics.decrementDlqSize("main");
        return ResponseEntity.ok(Map.of("cleared", current, "newSize", errorMetrics.getDlqSize("main")));
    }
}
