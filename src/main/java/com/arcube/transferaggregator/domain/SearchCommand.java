package com.arcube.transferaggregator.domain;

import lombok.Builder;
import java.time.LocalDateTime;

// Command for search operation
@Builder
public record SearchCommand(
    Location pickupLocation,
    Location dropoffLocation,
    LocalDateTime pickupDateTime,
    int numPassengers,
    int numBags,
    String currency,
    TransferMode mode
) {
    public enum TransferMode { ONE_WAY, ROUND_TRIP, HOURLY }
}
