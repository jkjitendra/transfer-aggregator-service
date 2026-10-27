package com.arcube.transferaggregator.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Fixed-label workflow metrics; identifiers and error messages never become labels. */
@Component
public class WorkflowMetrics {
    private final MeterRegistry registry;

    public WorkflowMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void claim(String kind, String result) {
        Counter.builder("transfer.idempotency.claims")
            .tag("kind", kind(kind)).tag("result", claimResult(result))
            .register(registry).increment();
    }

    public void write(String kind, String state, Duration elapsed) {
        Timer.builder("transfer.workflow.write.duration")
            .tag("kind", kind(kind)).tag("state", state(state))
            .publishPercentileHistogram().register(registry).record(elapsed);
    }

    public void cancellation(String state) {
        Counter.builder("transfer.cancellation.transitions")
            .tag("state", state(state)).register(registry).increment();
    }

    public void workerClaim(int count) {
        if (count > 0) Counter.builder("transfer.cancellation.worker.claims")
            .register(registry).increment(count);
    }

    private String kind(String value) {
        return "BOOKING".equals(value) || "CHANGE".equals(value) ? value : "OTHER";
    }

    private String claimResult(String value) {
        return "acquired".equals(value) || "replay".equals(value) || "conflict".equals(value)
            ? value : "other";
    }

    private String state(String value) {
        return switch (value) {
            case "CONFIRMED", "FAILED", "UNKNOWN_OUTCOME", "REQUESTED", "IN_PROGRESS",
                 "RETRY_SCHEDULED", "DLQ", "SUCCEEDED", "RESOLVED", "MANUAL_REVIEW" -> value;
            default -> "OTHER";
        };
    }
}
