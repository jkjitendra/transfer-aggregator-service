package com.arcube.transferaggregator.domain;

// Location - can be address, IATA code, or coordinates
public record Location(
    String address,
    String iataCode,
    String placeId,
    Double latitude,
    Double longitude,
    String timezone,
    String formattedAddress
) {}
