package com.arcube.transferaggregator.domain;

import lombok.Builder;
import java.time.Instant;
import java.util.List;
import java.util.Map;

// Transfer offer from a supplier
@Builder
public record Offer(
    String offerId,
    String supplierCode,
    Vehicle vehicle,
    Provider provider,
    Money totalPrice,
    CancellationPolicy cancellation,
    int estimatedDurationMinutes,
    Integer distanceMeters,        // Mozio: distance_meters
    boolean flightInfoRequired,
    boolean extraPassengerInfoRequired,
    Instant expiresAt,
    List<Amenity> includedAmenities,
    List<Amenity> availableAmenities,
    // Extensibility field for vendor-specific attributes
    Map<String, Object> extras
) {}

