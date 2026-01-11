package com.arcube.transferaggregator.domain;

import lombok.Builder;

// Vehicle information
@Builder
public record Vehicle(
    String type,
    String category,
    String vehicleClass,
    String image,
    int maxPassengers,
    int maxBags,
    String make,
    String model
) {}
