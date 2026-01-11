package com.arcube.transferaggregator.adapters.idcodec;

/** Payload encoded in bookingId */
public record BookingPayload(
    String supplierCode,
    String reservationId,
    String confirmationNumber
) {
    public static BookingPayload of(String supplierCode, String reservationId, String confirmationNumber) {
        return new BookingPayload(supplierCode, reservationId, confirmationNumber);
    }
}
