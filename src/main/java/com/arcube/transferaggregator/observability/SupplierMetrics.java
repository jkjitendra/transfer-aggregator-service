package com.arcube.transferaggregator.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Metrics for supplier operations.
 * Exposes histograms, counters, and gauges for monitoring.
 */
@Slf4j
@Component
public class SupplierMetrics {
    
    private final MeterRegistry registry;
    
    // Search metrics
    private final Timer searchLatencyTimer;
    private final DistributionSummary searchResultsDistribution;
    private final Counter searchErrorCounter;
    private final Counter searchTimeoutCounter;
    
    // Booking metrics
    private final Timer bookLatencyTimer;
    private final Counter bookSuccessCounter;
    private final Counter bookFailureCounter;
    
    // Poll metrics
    private final Counter pollCounter;
    
    public SupplierMetrics(MeterRegistry registry) {
        this.registry = registry;
        
        // Search latency histogram
        this.searchLatencyTimer = Timer.builder("transfer.search.latency")
            .description("Search operation latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);
        
        // Results count distribution
        this.searchResultsDistribution = DistributionSummary.builder("transfer.search.results")
            .description("Number of results per search")
            .publishPercentiles(0.5, 0.95)
            .register(registry);
        
        // Error counters
        this.searchErrorCounter = Counter.builder("transfer.search.errors")
            .description("Search error count")
            .register(registry);
        
        this.searchTimeoutCounter = Counter.builder("transfer.search.timeouts")
            .description("Search timeout count")
            .register(registry);
        
        // Booking metrics
        this.bookLatencyTimer = Timer.builder("transfer.book.latency")
            .description("Booking operation latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
        
        this.bookSuccessCounter = Counter.builder("transfer.book.success")
            .description("Successful bookings")
            .register(registry);
        
        this.bookFailureCounter = Counter.builder("transfer.book.failures")
            .description("Failed bookings")
            .register(registry);
        
        // Poll counter
        this.pollCounter = Counter.builder("transfer.supplier.polls")
            .description("Supplier poll count")
            .register(registry);
    }
    
    // Search metrics
    public void recordSearchLatency(String supplierCode, Duration duration) {
        Timer.builder("transfer.search.latency")
            .tag("supplier", supplierCode)
            .register(registry)
            .record(duration);
    }
    
    public void recordSearchResults(String supplierCode, int count) {
        DistributionSummary.builder("transfer.search.results")
            .tag("supplier", supplierCode)
            .register(registry)
            .record(count);
    }
    
    public void recordSearchError(String supplierCode, String errorType) {
        Counter.builder("transfer.search.errors")
            .tag("supplier", supplierCode)
            .tag("error", errorType)
            .register(registry)
            .increment();
    }
    
    public void recordSearchTimeout(String supplierCode) {
        Counter.builder("transfer.search.timeouts")
            .tag("supplier", supplierCode)
            .register(registry)
            .increment();
    }
    
    // Booking metrics
    public void recordBookLatency(String supplierCode, Duration duration) {
        Timer.builder("transfer.book.latency")
            .tag("supplier", supplierCode)
            .register(registry)
            .record(duration);
    }
    
    public void recordBookSuccess(String supplierCode) {
        Counter.builder("transfer.book.success")
            .tag("supplier", supplierCode)
            .register(registry)
            .increment();
    }
    
    public void recordBookFailure(String supplierCode, String reason) {
        Counter.builder("transfer.book.failures")
            .tag("supplier", supplierCode)
            .tag("reason", reason)
            .register(registry)
            .increment();
    }
    
    // Poll metrics
    public void recordPoll(String supplierCode, String operation) {
        Counter.builder("transfer.supplier.polls")
            .tag("supplier", supplierCode)
            .tag("operation", operation)
            .register(registry)
            .increment();
    }
    
    // Cancel metrics
    public void recordCancelSuccess(String supplierCode) {
        Counter.builder("transfer.cancel.success")
            .tag("supplier", supplierCode)
            .register(registry)
            .increment();
    }
    
    public void recordCancelFailure(String supplierCode, String reason) {
        Counter.builder("transfer.cancel.failures")
            .tag("supplier", supplierCode)
            .tag("reason", reason)
            .register(registry)
            .increment();
    }
}
