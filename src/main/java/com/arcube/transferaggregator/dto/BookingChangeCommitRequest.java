package com.arcube.transferaggregator.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingChangeCommitRequest {

    @NotBlank(message = "Old booking ID is required")
    private String oldBookingId;

    @NotBlank(message = "Search ID is required")
    private String searchId;

    @NotBlank(message = "Result ID (new offer) is required")
    private String resultId;

    // Optional: update contact info
    private String email;
    private String phoneNumber;
    private String countryCode;
    private String specialInstructions;

    // If price increased, use existing payment method
    private boolean useExistingPayment;
}
