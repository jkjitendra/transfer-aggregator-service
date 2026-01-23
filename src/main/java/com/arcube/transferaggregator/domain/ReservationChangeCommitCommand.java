package com.arcube.transferaggregator.domain;

import lombok.Builder;

@Builder
public record ReservationChangeCommitCommand(
    String oldReservationId,        // Mozio: old_reservation_id
    String searchId,                // From re-search result
    String resultId,                // Selected result from re-search
    
    // Optional contact updates
    String email,
    String phoneNumber,
    String countryCode,             // Mozio: country_code_name
    String specialInstructions,
    
    // Payment
    boolean useExistingPayment      // Mozio: use_reservation_card
) {}
