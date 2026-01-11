package com.arcube.transferaggregator.ports;

import com.arcube.transferaggregator.domain.BookingStatus;
import com.arcube.transferaggregator.domain.Money;

/** Result of a supplier booking operation */
public record SupplierBookingResult(
    String supplierCode,
    String reservationId,
    String confirmationNumber,
    BookingStatus status,
    Money totalPrice,
    String pickupInstructions,
    String errorCode,
    String errorMessage
) {
    public static SupplierBookingResult confirmed(String supplierCode, String reservationId,
            String confirmationNumber, Money totalPrice, String pickupInstructions) {
        return new SupplierBookingResult(supplierCode, reservationId, confirmationNumber,
            BookingStatus.CONFIRMED, totalPrice, pickupInstructions, null, null);
    }
    
    public static SupplierBookingResult pending(String supplierCode, String reservationId) {
        return new SupplierBookingResult(supplierCode, reservationId, null,
            BookingStatus.PENDING, null, null, null, null);
    }
    
    public static SupplierBookingResult failed(String supplierCode, String errorCode, String errorMessage) {
        return new SupplierBookingResult(supplierCode, null, null,
            BookingStatus.FAILED, null, null, errorCode, errorMessage);
    }
    
    public static SupplierBookingResult priceChanged(String supplierCode) {
        return new SupplierBookingResult(supplierCode, null, null,
            BookingStatus.PRICE_CHANGED, null, null, "PRICE_CHANGED", 
            "Price changed; please re-search to confirm latest price");
    }
    
    public boolean isSuccess() { return status == BookingStatus.CONFIRMED; }
    public boolean isPending() { return status == BookingStatus.PENDING; }
}
