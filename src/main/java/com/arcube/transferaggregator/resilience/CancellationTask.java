package com.arcube.transferaggregator.resilience;

import java.time.Instant;

/** Represents a cancellation task in the retry queue */
public record CancellationTask(
    String bookingId,
    String supplierCode,
    String reservationId,
    String confirmationNumber,
    Instant createdAt,
    int retryCount,
    String lastError
) {
    private static final int MAX_RETRIES = 3;
    
    public static CancellationTask create(String bookingId, String supplierCode, 
                                          String reservationId, String confirmationNumber) {
        return new CancellationTask(bookingId, supplierCode, reservationId, 
            confirmationNumber, Instant.now(), 0, null);
    }
    
    public CancellationTask withRetry(String error) {
        return new CancellationTask(bookingId, supplierCode, reservationId, 
            confirmationNumber, createdAt, retryCount + 1, error);
    }
    
    public boolean hasRetriesRemaining() {
        return retryCount < MAX_RETRIES;
    }
    
    public boolean isExpired() {
        // Tasks older than 1 hour are considered expired
        return Instant.now().isAfter(createdAt.plusSeconds(3600));
    }
}
