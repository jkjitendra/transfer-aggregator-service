package com.arcube.transferaggregator.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/** Bounded database snapshot so a Prometheus scrape never waits on PostgreSQL. */
@Slf4j
@Component
@Profile("!demo & !test")
public class WorkflowQueueMetrics {
    private final JdbcTemplate jdbc;
    private final AtomicLong reviewDepth = new AtomicLong();
    private final AtomicLong pendingDepth = new AtomicLong();
    private final AtomicLong oldestPendingSeconds = new AtomicLong();
    private final AtomicLong dlqDepth = new AtomicLong();
    private final AtomicLong snapshotHealthy = new AtomicLong();

    public WorkflowQueueMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        Gauge.builder("transfer.workflow.review.depth", reviewDepth, AtomicLong::get).register(registry);
        Gauge.builder("transfer.cancellation.pending.depth", pendingDepth, AtomicLong::get).register(registry);
        Gauge.builder("transfer.cancellation.pending.oldest.seconds", oldestPendingSeconds, AtomicLong::get)
            .register(registry);
        Gauge.builder("transfer.cancellation.dlq.depth", dlqDepth, AtomicLong::get).register(registry);
        Gauge.builder("transfer.workflow.snapshot.healthy", snapshotHealthy, AtomicLong::get).register(registry);
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 5_000)
    public void refresh() {
        try {
            reviewDepth.set(count("SELECT count(*) FROM operations " +
                "WHERE state IN ('UNKNOWN_OUTCOME','MANUAL_REVIEW')"));
            pendingDepth.set(count("SELECT count(*) FROM cancellation_jobs " +
                "WHERE state IN ('REQUESTED','RETRY_SCHEDULED')"));
            Long oldest = jdbc.queryForObject("SELECT coalesce(extract(epoch from " +
                "(now() - min(created_at)))::bigint,0) FROM cancellation_jobs " +
                "WHERE state IN ('REQUESTED','RETRY_SCHEDULED')", Long.class);
            oldestPendingSeconds.set(Math.max(0, oldest == null ? 0 : oldest));
            dlqDepth.set(count("SELECT count(*) FROM dead_letters d JOIN cancellation_jobs j " +
                "ON j.id=d.job_id WHERE j.state='DLQ' AND d.resolved_at IS NULL"));
            snapshotHealthy.set(1);
        } catch (RuntimeException exception) {
            snapshotHealthy.set(0);
            log.warn("Workflow queue metric refresh failed: {}", exception.getClass().getSimpleName());
        }
    }

    private long count(String sql) {
        Long count = jdbc.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }
}
