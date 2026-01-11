package com.arcube.transferaggregator.dto;

import com.arcube.transferaggregator.domain.BookingStatus;
import com.arcube.transferaggregator.domain.Money;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelResponse {
    private String bookingId;
    private String status;
    private Money refundAmount;
    private Instant refundedAt;
    private String message;
    
    public static CancelResponse success(String bookingId, Money refundAmount) {
        return CancelResponse.builder()
            .bookingId(bookingId)
            .status(BookingStatus.CANCELLED.name())
            .refundAmount(refundAmount)
            .refundedAt(Instant.now())
            .message("Booking cancelled successfully")
            .build();
    }
    
    public static CancelResponse alreadyCancelled(String bookingId) {
        return CancelResponse.builder()
            .bookingId(bookingId)
            .status(BookingStatus.CANCELLED.name())
            .message("Booking was already cancelled")
            .build();
    }
    
    public static CancelResponse pending(String bookingId, String message) {
        return CancelResponse.builder()
            .bookingId(bookingId)
            .status(BookingStatus.PENDING.name())
            .message(message)
            .build();
    }
    
    public static CancelResponse failed(String bookingId, String message) {
        return CancelResponse.builder()
            .bookingId(bookingId)
            .status(BookingStatus.FAILED.name())
            .message(message)
            .build();
    }
}

