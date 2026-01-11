package com.arcube.transferaggregator.domain;

import lombok.Builder;
import java.util.List;

// Command for booking operation
@Builder
public record BookCommand(
    String searchId,
    String resultId,
    PassengerInfo passenger,
    FlightInfo flight,
    List<ExtraPassenger> extraPassengers,
    String specialInstructions,
    String partnerTrackingId
) {
    public record PassengerInfo(
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String countryCode
    ) {}
    
    public record FlightInfo(String airline, String flightNumber) {}
    
    public record ExtraPassenger(String firstName, String lastName) {}
}
