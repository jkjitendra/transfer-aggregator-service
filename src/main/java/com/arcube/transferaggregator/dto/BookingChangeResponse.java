package com.arcube.transferaggregator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingChangeResponse {

    private String status;  // SUCCESS, PENDING, FAILED
    private String newBookingId;
    private String newConfirmationNumber;
    private String oldBookingId;
    private String errorCode;
    private String errorMessage;

    public static BookingChangeResponse success(String newBookingId, String confirmationNumber, String oldBookingId) {
        return BookingChangeResponse.builder()
            .status("SUCCESS")
            .newBookingId(newBookingId)
            .newConfirmationNumber(confirmationNumber)
            .oldBookingId(oldBookingId)
            .build();
    }

    public static BookingChangeResponse pending(String oldBookingId) {
        return BookingChangeResponse.builder()
            .status("PENDING")
            .oldBookingId(oldBookingId)
            .build();
    }

    public static BookingChangeResponse failed(String errorCode, String errorMessage) {
        return BookingChangeResponse.builder()
            .status("FAILED")
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .build();
    }
}
