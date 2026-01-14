package com.arcube.transferaggregator.domain;

import lombok.Builder;

@Builder
public record ChangeBookingCommand(
    String oldReservationId,
    String newSearchId,
    String newResultId,
    String email,
    String phoneNumber,
    String countryCode,
    String specialInstructions
) {}
