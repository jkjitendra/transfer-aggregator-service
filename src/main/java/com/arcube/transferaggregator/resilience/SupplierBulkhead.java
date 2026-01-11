package com.arcube.transferaggregator.resilience;

import com.arcube.transferaggregator.config.AggregatorProperties;
import com.arcube.transferaggregator.exception.ServiceBusyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Semaphore-based bulkhead to limit concurrent supplier calls.
 * Prevents the system from being overwhelmed by too many concurrent requests.
 */
@Slf4j
@Component
public class SupplierBulkhead {
    
    private final Semaphore semaphore;
    private final long acquireTimeoutMs;
    
    public SupplierBulkhead(AggregatorProperties properties) {
        int maxConcurrent = properties.getResilience().getMaxConcurrentCalls();
        this.semaphore = new Semaphore(maxConcurrent, true);
        this.acquireTimeoutMs = 500; // Wait 500ms max for permit
        log.info("SupplierBulkhead initialized with {} permits", maxConcurrent);
    }
    
    /**
     * Execute a supplier call within the bulkhead.
     * 
     * @param action The action to execute
     * @param <T> Return type
     * @return Result of the action
     * @throws ServiceBusyException if bulkhead is full
     */
    public <T> T execute(Supplier<T> action) {
        boolean acquired = false;
        try {
            acquired = semaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("Bulkhead full - {} permits available", semaphore.availablePermits());
                throw new ServiceBusyException("Service is temporarily busy, please retry");
            }
            
            log.debug("Bulkhead permit acquired - {} remaining", semaphore.availablePermits());
            return action.get();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceBusyException("Request interrupted");
        } finally {
            if (acquired) {
                semaphore.release();
                log.debug("Bulkhead permit released - {} available", semaphore.availablePermits());
            }
        }
    }
    
    /** Execute a runnable within the bulkhead */
    public void execute(Runnable action) {
        execute(() -> {
            action.run();
            return null;
        });
    }
    
    /** Get current available permits */
    public int availablePermits() {
        return semaphore.availablePermits();
    }
}
