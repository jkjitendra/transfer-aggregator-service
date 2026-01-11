package com.arcube.transferaggregator.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;
import java.util.Queue;

/**
 * In-memory cancellation queue with retry support.
 * 
 * For production: Replace with Redis/SQS/PostgreSQL for persistence.
 */
@Slf4j
@Component
public class CancellationQueue {
    
    // Main retry queue
    private final Queue<CancellationTask> queue = new ConcurrentLinkedQueue<>();
    
    // Track pending cancellations by bookingId for status lookup
    private final Map<String, CancellationTask> pendingCancellations = new ConcurrentHashMap<>();
    
    /** Add a cancellation task to the queue */
    public void enqueue(CancellationTask task) {
        queue.offer(task);
        pendingCancellations.put(task.bookingId(), task);
        log.info("Queued cancellation: bookingId={}, retry={}", task.bookingId(), task.retryCount());
    }
    
    /** Get next task for processing */
    public Optional<CancellationTask> poll() {
        return Optional.ofNullable(queue.poll());
    }
    
    /** Re-queue a task for retry */
    public void requeue(CancellationTask task) {
        CancellationTask updated = task.withRetry(task.lastError());
        queue.offer(updated);
        pendingCancellations.put(task.bookingId(), updated);
        log.info("Re-queued cancellation: bookingId={}, retry={}", task.bookingId(), updated.retryCount());
    }
    
    /** Mark cancellation as completed (success or moved to DLQ) */
    public void complete(String bookingId) {
        pendingCancellations.remove(bookingId);
    }
    
    /** Check if a booking has a pending cancellation */
    public Optional<CancellationTask> getPending(String bookingId) {
        return Optional.ofNullable(pendingCancellations.get(bookingId));
    }
    
    /** Get queue size for monitoring */
    public int size() {
        return queue.size();
    }
    
    /** Get pending count for monitoring */
    public int pendingCount() {
        return pendingCancellations.size();
    }
}
