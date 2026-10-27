package com.arcube.transferaggregator.domain;

import lombok.Builder;

@Builder
public record ReservationChangeCommitCommand(
    String oldReservationId,
    String searchId,                // From re-search result
    String resultId,                // Selected result from re-search
    
    // Optional contact updates
    String email,
    String phoneNumber,
    String countryCode,
    String specialInstructions,
    
    // Payment
    boolean useExistingPayment
) {}
