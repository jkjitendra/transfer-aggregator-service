package com.arcube.transferaggregator.controller;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;

/** Fixed, aggregate operator snapshot from the same meters scraped by Prometheus. */
@RestController
@RequestMapping("/api/v1/admin/visual")
public class VisualObservabilityController {
    private final MeterRegistry meters;
    private final String grafanaBaseUrl;

    public VisualObservabilityController(MeterRegistry meters,
            @Value("${visual.grafana-base-url:}") String grafanaBaseUrl) {
        this.meters = meters;
        this.grafanaBaseUrl = safeGrafanaUrl(grafanaBaseUrl);
    }

    public record Snapshot(Instant sampledAt, long searchComplete, long searchPartial,
                           long supplierTimeout, long circuitOpen, long claimsAcquired,
                           long claimsReplay, long claimsConflict, long reviewDepth,
                           long pendingCancellations, long deadLetters, double bulkheadSaturation,
                           String dashboardUrl) {}

    @GetMapping("/metrics")
    public ResponseEntity<Snapshot> metrics() {
        return ResponseEntity.ok()
            .cacheControl(org.springframework.http.CacheControl.noStore())
            .body(new Snapshot(Instant.now(), timerCount("transfer.request.search.duration", "outcome", "complete"),
                timerCount("transfer.request.search.duration", "outcome", "partial"),
                timerCount("transfer.supplier.search.duration", "outcome", "TIMEOUT"),
                timerCount("transfer.supplier.search.duration", "outcome", "CIRCUIT_OPEN"),
                counterCount("transfer.idempotency.claims", "result", "acquired"),
                counterCount("transfer.idempotency.claims", "result", "replay"),
                counterCount("transfer.idempotency.claims", "result", "conflict"),
                gauge("transfer.workflow.review.depth"), gauge("transfer.cancellation.pending.depth"),
                gauge("transfer.cancellation.dlq.depth"),
                Math.max(0, Math.min(1, gaugeValue("transfer.bulkhead.saturation"))),
                grafanaBaseUrl.isEmpty() ? null : grafanaBaseUrl + "/d/transfer-operations/transfer-aggregator-operations"));
    }

    private long timerCount(String name, String tag, String value) {
        return meters.find(name).tag(tag, value).timers().stream().mapToLong(Timer::count).sum();
    }

    private long counterCount(String name, String tag, String value) {
        return Math.round(meters.find(name).tag(tag, value).counters().stream()
            .mapToDouble(counter -> counter.count()).sum());
    }

    private long gauge(String name) {
        return Math.max(0, Math.round(gaugeValue(name)));
    }

    private double gaugeValue(String name) {
        return meters.find(name).gauges().stream().mapToDouble(gauge -> gauge.value())
            .filter(Double::isFinite).sum();
    }

    private static String safeGrafanaUrl(String candidate) {
        if (candidate == null || candidate.isBlank()) return "";
        URI uri = URI.create(candidate);
        boolean secure = "https".equals(uri.getScheme());
        boolean loopback = "http".equals(uri.getScheme()) &&
            ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if ((!secure && !loopback) || uri.getUserInfo() != null || uri.getQuery() != null ||
                uri.getFragment() != null || uri.getHost() == null) {
            throw new IllegalArgumentException("Grafana URL must be HTTPS or loopback HTTP without credentials");
        }
        return candidate.replaceAll("/+$", "");
    }
}
