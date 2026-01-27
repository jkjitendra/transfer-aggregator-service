package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.adapters.idcodec.BookingIdCodec;
import com.arcube.transferaggregator.adapters.idcodec.BookingPayload;
import com.arcube.transferaggregator.domain.CancelCommand;
import com.arcube.transferaggregator.dto.CancelResponse;
import com.arcube.transferaggregator.exception.CancellationNotAllowedException;
import com.arcube.transferaggregator.exception.SupplierNotFoundException;
import com.arcube.transferaggregator.ports.SupplierCancelResult;
import com.arcube.transferaggregator.ports.SupplierRegistry;
import com.arcube.transferaggregator.ports.TransferSupplier;
import com.arcube.transferaggregator.resilience.CancellationDLQ;
import com.arcube.transferaggregator.resilience.CancellationQueue;
import com.arcube.transferaggregator.resilience.CancellationTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.*;

/** Orchestrates cancellation operations with resilient retry queue */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferCancellationService {
    
    private final SupplierRegistry supplierRegistry;
    private final BookingIdCodec bookingIdCodec;
    private final CancellationQueue cancellationQueue;
    private final CancellationDLQ cancellationDLQ;
    
    private static final long TIMEOUT_SECONDS = 5;
    
    public CancelResponse cancel(String bookingId) {
        BookingPayload payload = bookingIdCodec.decode(bookingId);
        log.info("Cancelling: supplier={}, reservationId={}", payload.supplierCode(), payload.reservationId());
        
        // Check if already in queue (pending retry)
        Optional<CancellationTask> pending = cancellationQueue.getPending(bookingId);
        if (pending.isPresent()) {
            log.info("Cancellation already pending: bookingId={}, retry={}", bookingId, pending.get().retryCount());
            return CancelResponse.pending(bookingId, "Cancellation in progress, please check status");
        }
        
        // Check if in DLQ (failed)
        Optional<CancellationTask> dlq = cancellationDLQ.get(bookingId);
        if (dlq.isPresent()) {
            log.warn("Cancellation in DLQ: bookingId={}", bookingId);
            return CancelResponse.failed(bookingId, "Cancellation failed. Please contact support.");
        }
        
        TransferSupplier supplier = supplierRegistry.getSupplier(payload.supplierCode())
            .orElseThrow(() -> new SupplierNotFoundException(payload.supplierCode()));
        
        CancelCommand command = CancelCommand.of(payload.reservationId(), payload.supplierCode());
        
        // Try cancellation with timeout
        try {
            SupplierCancelResult result = executeWithTimeout(supplier, command);
            return handleResult(bookingId, payload, result);
        } catch (TimeoutException e) {
            // Supplier didn't respond in time - queue for retry
            log.warn("Cancellation timeout, queuing for retry: bookingId={}", bookingId);
            queueForRetry(bookingId, payload);
            return CancelResponse.pending(bookingId, "Cancellation in progress, please check status");
        } catch (Exception e) {
            log.error("Cancellation error: bookingId={}, error={}", bookingId, e.getMessage());
            queueForRetry(bookingId, payload);
            return CancelResponse.pending(bookingId, "Cancellation in progress, please check status");
        }
    }
    
    /** Check the status of a cancellation */
    public CancelResponse getStatus(String bookingId) {
        // Check if pending in queue
        Optional<CancellationTask> pending = cancellationQueue.getPending(bookingId);
        if (pending.isPresent()) {
            return CancelResponse.pending(bookingId, 
                String.format("Retry %d/3 in progress", pending.get().retryCount()));
        }
        
        // Check if in DLQ
        Optional<CancellationTask> dlq = cancellationDLQ.get(bookingId);
        if (dlq.isPresent()) {
            return CancelResponse.failed(bookingId, 
                "Cancellation failed after 3 retries. Error: " + dlq.get().lastError());
        }
        
        // Not in queue - either completed or never started
        return CancelResponse.builder()
            .bookingId(bookingId)
            .status("UNKNOWN")
            .message("No pending cancellation. May have completed successfully.")
            .build();
    }
    
    protected SupplierCancelResult executeWithTimeout(TransferSupplier supplier, CancelCommand command) 
            throws TimeoutException, ExecutionException, InterruptedException {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<SupplierCancelResult> future = executor.submit(() -> supplier.cancel(command));
            try {
                return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }
        }
    }
    
    private CancelResponse handleResult(String bookingId, BookingPayload payload, SupplierCancelResult result) {
        if (!result.isSuccess()) {
            if ("TOO_LATE_TO_CANCEL".equals(result.errorCode())) {
                throw new CancellationNotAllowedException(bookingId, result.errorMessage());
            }
            throw new RuntimeException("Cancellation failed: " + result.errorMessage());
        }
        
        log.info("Cancellation result: alreadyCancelled={}", result.alreadyCancelled());
        
        if (result.alreadyCancelled()) {
            return CancelResponse.alreadyCancelled(bookingId);
        }
        return CancelResponse.success(bookingId, result.refundAmount());
    }
    
    private void queueForRetry(String bookingId, BookingPayload payload) {
        CancellationTask task = CancellationTask.create(
            bookingId,
            payload.supplierCode(),
            payload.reservationId(),
            payload.confirmationNumber()
        );
        cancellationQueue.enqueue(task);
    }
}
