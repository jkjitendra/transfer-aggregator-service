package com.arcube.transferaggregator.resilience;

import com.arcube.transferaggregator.domain.CancelCommand;
import com.arcube.transferaggregator.ports.SupplierCancelResult;
import com.arcube.transferaggregator.ports.SupplierRegistry;
import com.arcube.transferaggregator.ports.TransferSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Background worker that processes the cancellation retry queue.
 * Runs every 5 seconds to process pending cancellations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CancellationWorker {
    
    private final CancellationQueue queue;
    private final CancellationDLQ dlq;
    private final SupplierRegistry supplierRegistry;
    
    /** Process cancellation queue every 5 seconds */
    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void processCancellations() {
        int processed = 0;
        Optional<CancellationTask> taskOpt;
        
        while ((taskOpt = queue.poll()).isPresent()) {
            CancellationTask task = taskOpt.get();
            processed++;
            
            try {
                processTask(task);
            } catch (Exception e) {
                log.error("Error processing cancellation task: {}", task.bookingId(), e);
                handleFailure(task, e.getMessage());
            }
        }
        
        if (processed > 0) {
            log.info("Processed {} cancellation tasks. Queue size={}, DLQ size={}", 
                processed, queue.size(), dlq.size());
        }
    }
    
    private void processTask(CancellationTask task) {
        // Check if task is expired
        if (task.isExpired()) {
            log.warn("Cancellation task expired: bookingId={}", task.bookingId());
            dlq.add(task.withRetry("Task expired after 1 hour"));
            queue.complete(task.bookingId());
            return;
        }
        
        // Find the supplier
        Optional<TransferSupplier> supplierOpt = supplierRegistry.getEnabledSuppliers().stream()
            .filter(s -> s.getSupplierCode().equals(task.supplierCode()))
            .findFirst();
        
        if (supplierOpt.isEmpty()) {
            log.error("Supplier not found for cancellation: {}", task.supplierCode());
            dlq.add(task.withRetry("Supplier not available: " + task.supplierCode()));
            queue.complete(task.bookingId());
            return;
        }
        
        TransferSupplier supplier = supplierOpt.get();
        
        try {
            CancelCommand command = CancelCommand.of(
                task.reservationId(), 
                task.supplierCode()
            );
            
            SupplierCancelResult result = supplier.cancel(command);
            
            if (result.isSuccess()) {
                log.info("Cancellation succeeded on retry: bookingId={}, refund={}", 
                    task.bookingId(), result.refundAmount());
                queue.complete(task.bookingId());
            } else {
                handleFailure(task, result.errorMessage());
            }
            
        } catch (Exception e) {
            handleFailure(task, e.getMessage());
        }
    }
    
    private void handleFailure(CancellationTask task, String error) {
        CancellationTask updated = task.withRetry(error);
        
        if (updated.hasRetriesRemaining()) {
            log.warn("Cancellation retry {}/{} failed: bookingId={}, error={}", 
                updated.retryCount(), 3, task.bookingId(), error);
            queue.requeue(updated);
        } else {
            log.error("Cancellation exhausted retries: bookingId={}", task.bookingId());
            dlq.add(updated);
            queue.complete(task.bookingId());
        }
    }
}
