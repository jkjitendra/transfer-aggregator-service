package com.arcube.transferaggregator.observability;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierMetricsTest {

    @Test
    void recordsMetricsPerSupplier() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SupplierMetrics metrics = new SupplierMetrics(registry);

        metrics.recordSearchLatency("S1", Duration.ofMillis(50));
        metrics.recordSearchResults("S1", 3);
        metrics.recordSearchError("S1", "timeout");
        metrics.recordSearchTimeout("S1");
        metrics.recordBookLatency("S1", Duration.ofMillis(80));
        metrics.recordBookSuccess("S1");
        metrics.recordBookFailure("S1", "failed");
        metrics.recordPoll("S1", "search");
        metrics.recordCancelSuccess("S1");
        metrics.recordCancelFailure("S1", "failed");
        metrics.recordSearchOutcome("S1", "TIMEOUT", Duration.ofMillis(75));
        metrics.recordSearchRequest(true, Duration.ofMillis(100));

        DistributionSummary summary = registry.find("transfer.search.results")
            .tag("supplier", "S1")
            .summary();
        assertThat(summary).isNotNull();
        assertThat(summary.count()).isEqualTo(1);
        assertThat(registry.find("transfer.supplier.search.duration")
            .tags("supplier", "S1", "outcome", "TIMEOUT").timer().count()).isEqualTo(1);
        assertThat(registry.find("transfer.request.search.duration")
            .tag("outcome", "partial").timer().count()).isEqualTo(1);
    }
}
