package com.arcube.transferaggregator.resilience;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** Dead Letter Queue for cancellations that exhausted all retries */
@Slf4j
@Component
public class CancellationDLQ {
    
    private final Map<String, CancellationTask> deadLetters = new ConcurrentHashMap<>();
    private final Counter dlqCounter;
    
    public CancellationDLQ(MeterRegistry meterRegistry) {
        // Counter for total failures sent to DLQ
        this.dlqCounter = Counter.builder("transfer.cancellation.dlq.total")
            .description("Total cancellations sent to dead letter queue")
            .tag("application", "transfer-aggregator")
            .register(meterRegistry);
        
        // Gauge for current DLQ size (for dashboards)
        Gauge.builder("transfer.cancellation.dlq.size", deadLetters, Map::size)
            .description("Current number of items in cancellation DLQ")
            .tag("application", "transfer-aggregator")
            .register(meterRegistry);
    }
    
    /** Add a failed task to DLQ  */
    public void add(CancellationTask task) {
        deadLetters.put(task.bookingId(), task);
        dlqCounter.increment();
        
        log.error("DLQ: Cancellation failed after {} retries: bookingId={}, supplier={}, error={}", 
            task.retryCount(), task.bookingId(), task.supplierCode(), task.lastError());
        
        // Todo: send alert here
        // alertService.sendCriticalAlert("Cancellation failed: " + task.bookingId());
    }
    
    /** Get a dead letter by bookingId */
    public Optional<CancellationTask> get(String bookingId) {
        return Optional.ofNullable(deadLetters.get(bookingId));
    }
    
    /** Remove from DLQ after manual resolution */
    public void resolve(String bookingId) {
        CancellationTask removed = deadLetters.remove(bookingId);
        if (removed != null) {
            log.info("DLQ: Resolved bookingId={}", bookingId);
        }
    }
    
    /** Get all dead letters for admin review */
    public List<CancellationTask> getAll() {
        return List.copyOf(deadLetters.values());
    }
    
    /** Get DLQ size for monitoring/alerting */
    public int size() {
        return deadLetters.size();
    }
}

