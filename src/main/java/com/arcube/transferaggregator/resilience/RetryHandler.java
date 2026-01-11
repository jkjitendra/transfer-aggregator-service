package com.arcube.transferaggregator.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Simple retry mechanism for transient failures.
 * Uses exponential backoff between retries.
 */
@Slf4j
@Component
public class RetryHandler {
    
    private static final int DEFAULT_MAX_RETRIES = 2;
    private static final long INITIAL_BACKOFF_MS = 100;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    
    /**
     * Execute with retry for transient failures.
     * 
     * @param action The action to execute
     * @param maxRetries Maximum number of retries
     * @param <T> Return type
     * @return Result of the action
     */
    public <T> T executeWithRetry(Supplier<T> action, int maxRetries) {
        RuntimeException lastException = null;
        long backoffMs = INITIAL_BACKOFF_MS;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    log.debug("Retry attempt {} after {}ms", attempt, backoffMs);
                }
                return action.get();
            } catch (RuntimeException e) {
                lastException = e;
                
                if (!isRetryable(e)) {
                    log.debug("Non-retryable exception: {}", e.getClass().getSimpleName());
                    throw e;
                }
                
                if (attempt < maxRetries) {
                    log.warn("Transient failure (attempt {}), retrying in {}ms: {}", 
                        attempt + 1, backoffMs, e.getMessage());
                    sleep(backoffMs);
                    backoffMs = (long) (backoffMs * BACKOFF_MULTIPLIER);
                }
            }
        }
        
        log.error("All {} retries exhausted", maxRetries);
        throw Objects.requireNonNull(lastException, "No exception captured");
    }
    
    /**
     * Execute with default retry settings.
     */
    public <T> T executeWithRetry(Supplier<T> action) {
        return executeWithRetry(action, DEFAULT_MAX_RETRIES);
    }
    
    /**
     * Determine if an exception is retryable (transient).
     */
    private boolean isRetryable(RuntimeException e) {
        String name = e.getClass().getSimpleName();
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        
        // Retryable: network issues, timeouts, 5xx responses
        return name.contains("Timeout") ||
               name.contains("Connect") ||
               name.contains("IOException") ||
               message.contains("timeout") ||
               message.contains("connection") ||
               message.contains("temporarily unavailable") ||
               message.contains("503") ||
               message.contains("502") ||
               message.contains("504");
    }
    
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry interrupted", e);
        }
    }
}
