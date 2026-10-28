package com.arcube.transferaggregator.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisualObservabilityControllerTest {
    @Test
    void exposesOnlyFixedAggregatesAndSafeDashboardLink() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        meters.timer("transfer.request.search.duration", "outcome", "partial", "tenant", "tenant-a")
            .record(Duration.ofMillis(50));
        meters.timer("transfer.request.search.duration", "outcome", "partial", "tenant", "tenant-b")
            .record(Duration.ofMillis(50));
        Counter.builder("transfer.idempotency.claims").tag("result", "replay")
            .tag("kind", "BOOKING").register(meters).increment();

        var result = new VisualObservabilityController(meters, "http://localhost:3000/")
            .metrics().getBody();
        assertThat(result).isNotNull();
        assertThat(result.searchPartial()).isEqualTo(2);
        assertThat(result.claimsReplay()).isEqualTo(1);
        assertThat(result.dashboardUrl()).isEqualTo(
            "http://localhost:3000/d/transfer-operations/transfer-aggregator-operations");
        assertThat(result.toString()).doesNotContain("tenant-a", "tenant-b");
        assertThatThrownBy(() -> new VisualObservabilityController(meters,
            "https://operator:secret@grafana.example.test"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
