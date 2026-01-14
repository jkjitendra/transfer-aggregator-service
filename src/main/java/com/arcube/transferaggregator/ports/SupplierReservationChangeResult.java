package com.arcube.transferaggregator.ports;

import com.arcube.transferaggregator.domain.BookingStatus;
import com.arcube.transferaggregator.domain.Money;
import lombok.Builder;

@Builder
public record SupplierReservationChangeResult(
    BookingStatus status,
    String newReservationId,
    String newConfirmationNumber,
    String oldReservationId,
    Money newTotalPrice,
    Money priceDifference,          // Positive = customer owes more, Negative = refund
    String errorCode,
    String errorMessage
) {
    public static SupplierReservationChangeResult success(
            String newReservationId, String confirmationNumber, 
            String oldReservationId, Money newPrice, Money priceDiff) {
        return SupplierReservationChangeResult.builder()
            .status(BookingStatus.CONFIRMED)
            .newReservationId(newReservationId)
            .newConfirmationNumber(confirmationNumber)
            .oldReservationId(oldReservationId)
            .newTotalPrice(newPrice)
            .priceDifference(priceDiff)
            .build();
    }

    public static SupplierReservationChangeResult pending(String oldReservationId) {
        return SupplierReservationChangeResult.builder()
            .status(BookingStatus.PENDING)
            .oldReservationId(oldReservationId)
            .build();
    }

    public static SupplierReservationChangeResult failed(String errorCode, String errorMessage) {
        return SupplierReservationChangeResult.builder()
            .status(BookingStatus.FAILED)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .build();
    }
}
