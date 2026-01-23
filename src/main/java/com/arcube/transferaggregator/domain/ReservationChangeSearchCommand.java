package com.arcube.transferaggregator.domain;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ReservationChangeSearchCommand(
    String reservationId,           // Old reservation to change
    String supplierCode,            // Original supplier
    
    // Optional fields - only provide what needs to change
    Location newPickupLocation,
    Location newDropoffLocation,
    LocalDateTime newPickupDateTime,
    Integer newNumPassengers,
    String currency
) {}
