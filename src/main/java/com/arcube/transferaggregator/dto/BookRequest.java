package com.arcube.transferaggregator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookRequest {
    
    @NotBlank(message = "Offer ID is required")
    private String offerId;
    
    @NotNull(message = "Passenger info is required")
    @Valid
    private PassengerDto passenger;
    
    @Valid
    private FlightDto flight;
    
    private List<@Valid ExtraPassengerDto> extraPassengers;
    
    private String specialInstructions;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PassengerDto {
        @NotBlank private String firstName;
        @NotBlank private String lastName;
        @NotBlank @Email private String email;
        @NotBlank private String phoneNumber;
        @NotBlank private String countryCode;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlightDto {
        @NotBlank private String airline;
        @NotBlank private String flightNumber;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtraPassengerDto {
        @NotBlank private String firstName;
        @NotBlank private String lastName;
    }
}
