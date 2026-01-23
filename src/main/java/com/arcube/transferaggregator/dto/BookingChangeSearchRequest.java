package com.arcube.transferaggregator.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingChangeSearchRequest {

    @NotBlank(message = "Booking ID is required")
    private String bookingId;

    // Optional fields - only include what you want to change
    private LocationDto newPickupLocation;
    private LocationDto newDropoffLocation;
    private LocalDateTime newPickupDateTime;
    private Integer newNumPassengers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationDto {
        private String address;
        private String iataCode;
        private String placeId;
        private Double latitude;
        private Double longitude;
    }
}
