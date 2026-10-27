package com.arcube.transferaggregator.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowMetricsTest {
    @Test
    void recordsBoundedClaimAndStateLabels() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WorkflowMetrics metrics = new WorkflowMetrics(registry);
        metrics.claim("BOOKING", "acquired");
        metrics.claim("BOOKING", "raw-user-provided-value");
        metrics.write("CHANGE", "UNKNOWN_OUTCOME", Duration.ofMillis(25));
        metrics.cancellation("DLQ");
        metrics.workerClaim(3);

        assertThat(registry.find("transfer.idempotency.claims")
            .tags("kind", "BOOKING", "result", "acquired").counter().count()).isEqualTo(1);
        assertThat(registry.find("transfer.idempotency.claims")
            .tags("kind", "BOOKING", "result", "other").counter().count()).isEqualTo(1);
        assertThat(registry.find("transfer.workflow.write.duration")
            .tags("kind", "CHANGE", "state", "UNKNOWN_OUTCOME").timer().count()).isEqualTo(1);
        assertThat(registry.find("transfer.cancellation.transitions")
            .tag("state", "DLQ").counter().count()).isEqualTo(1);
        assertThat(registry.find("transfer.cancellation.worker.claims").counter().count()).isEqualTo(3);
    }
}
