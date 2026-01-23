package com.arcube.transferaggregator.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Metrics for error tracking and DLQ (Dead Letter Queue) monitoring.
 * Exposes Prometheus metrics for alerting.
 */
@Slf4j
@Component
public class ErrorMetrics {

    private final MeterRegistry meterRegistry;
    
    // Error counters per supplier
    private final Map<String, Counter> supplierErrorCounters = new ConcurrentHashMap<>();
    
    // DLQ size gauges (simulated - in production would be from actual queue)
    private final Map<String, AtomicInteger> dlqSizes = new ConcurrentHashMap<>();
    
    // Global error counters
    private Counter searchErrors;
    private Counter bookingErrors;
    private Counter cancellationErrors;
    private Counter pollingErrors;

    public ErrorMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        // Initialize global error counters
        searchErrors = Counter.builder("aggregator.errors")
            .tag("operation", "search")
            .description("Total search errors")
            .register(meterRegistry);
        
        bookingErrors = Counter.builder("aggregator.errors")
            .tag("operation", "booking")
            .description("Total booking errors")
            .register(meterRegistry);
        
        cancellationErrors = Counter.builder("aggregator.errors")
            .tag("operation", "cancellation")
            .description("Total cancellation errors")
            .register(meterRegistry);
        
        pollingErrors = Counter.builder("aggregator.errors")
            .tag("operation", "polling")
            .description("Total polling errors")
            .register(meterRegistry);

        // Initialize DLQ gauge for main queue
        dlqSizes.put("main", new AtomicInteger(0));
        Gauge.builder("aggregator.dlq.size", dlqSizes.get("main"), AtomicInteger::get)
            .tag("queue", "main")
            .description("Dead letter queue size")
            .register(meterRegistry);

        log.info("ErrorMetrics initialized with DLQ and error counters");
    }

    // ========== Error Recording ==========
    
    public void recordSearchError(String supplierCode, String errorType) {
        searchErrors.increment();
        getSupplierErrorCounter(supplierCode, "search").increment();
        log.debug("Recorded search error: supplier={}, type={}", supplierCode, errorType);
    }

    public void recordBookingError(String supplierCode, String errorType) {
        bookingErrors.increment();
        getSupplierErrorCounter(supplierCode, "booking").increment();
        log.debug("Recorded booking error: supplier={}, type={}", supplierCode, errorType);
    }

    public void recordCancellationError(String supplierCode, String errorType) {
        cancellationErrors.increment();
        getSupplierErrorCounter(supplierCode, "cancellation").increment();
        log.debug("Recorded cancellation error: supplier={}, type={}", supplierCode, errorType);
    }

    public void recordPollingError(String searchId, String errorType) {
        pollingErrors.increment();
        log.debug("Recorded polling error: searchId={}, type={}", searchId, errorType);
    }

    // ========== DLQ Management ==========

    public void incrementDlqSize(String queueName) {
        dlqSizes.computeIfAbsent(queueName, k -> {
            AtomicInteger size = new AtomicInteger(0);
            Gauge.builder("aggregator.dlq.size", size, AtomicInteger::get)
                .tag("queue", queueName)
                .description("Dead letter queue size for " + queueName)
                .register(meterRegistry);
            return size;
        }).incrementAndGet();
    }

    public void decrementDlqSize(String queueName) {
        AtomicInteger size = dlqSizes.get(queueName);
        if (size != null && size.get() > 0) {
            size.decrementAndGet();
        }
    }

    public int getDlqSize(String queueName) {
        AtomicInteger size = dlqSizes.get(queueName);
        return size != null ? size.get() : 0;
    }

    public int getTotalDlqSize() {
        return dlqSizes.values().stream()
            .mapToInt(AtomicInteger::get)
            .sum();
    }

    // ========== Helpers ==========

    private Counter getSupplierErrorCounter(String supplierCode, String operation) {
        String key = supplierCode + ":" + operation;
        return supplierErrorCounters.computeIfAbsent(key, k ->
            Counter.builder("aggregator.supplier.errors")
                .tag("supplier", supplierCode)
                .tag("operation", operation)
                .description("Errors per supplier and operation")
                .register(meterRegistry)
        );
    }

    // ========== Getters for AlertingService ==========
    
    public double getSearchErrorCount() {
        return searchErrors.count();
    }

    public double getBookingErrorCount() {
        return bookingErrors.count();
    }

    public double getCancellationErrorCount() {
        return cancellationErrors.count();
    }
}
