package com.arcube.transferaggregator.observability;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import com.arcube.transferaggregator.resilience.RetryHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PrometheusMetricContractTest {
    @Test
    void dashboardAndAlertsUseExportedMetricNames() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        new SupplierMetrics(registry).recordSearchOutcome("STUB", "TIMEOUT", Duration.ofMillis(100));
        new SupplierMetrics(registry).recordSearchRequest(true, Duration.ofMillis(120));
        WorkflowMetrics workflow = new WorkflowMetrics(registry);
        workflow.claim("BOOKING", "replay");
        workflow.write("BOOKING", "UNKNOWN_OUTCOME", Duration.ofMillis(50));
        workflow.cancellation("DLQ");
        new WorkflowQueueMetrics(mock(JdbcTemplate.class), registry).refresh();
        RetryHandler retries = new RetryHandler();
        ReflectionTestUtils.setField(retries, "meters", registry);
        AtomicInteger calls = new AtomicInteger();
        retries.executeReadWithinDeadline(() -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("timeout");
            return "ok";
        }, Instant.now().plusSeconds(1), 1);
        String scrape = registry.scrape();

        assertThat(scrape).contains("transfer_supplier_search_duration_seconds_count");
        assertThat(scrape).contains("transfer_request_search_duration_seconds_count");
        assertThat(scrape).contains("transfer_idempotency_claims_total");
        assertThat(scrape).contains("transfer_workflow_write_duration_seconds_count");
        assertThat(scrape).contains("transfer_cancellation_transitions_total");
        assertThat(scrape).contains("transfer_retry_attempts_total");
        assertThat(scrape).contains("transfer_cancellation_dlq_depth");
        assertThat(scrape).contains("transfer_cancellation_pending_oldest_seconds");
        assertThat(scrape).contains("transfer_workflow_review_depth");
        assertThat(scrape).doesNotContain("raw-user-provided-value");
    }
}
