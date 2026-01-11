package com.arcube.transferaggregator.ports;

import com.arcube.transferaggregator.domain.BookingStatus;
import com.arcube.transferaggregator.domain.Money;

/** Result of a supplier cancellation operation */
public record SupplierCancelResult(
    String supplierCode,
    String reservationId,
    BookingStatus status,
    Money refundAmount,
    boolean alreadyCancelled,
    String errorCode,
    String errorMessage
) {
    public static SupplierCancelResult success(String supplierCode, String reservationId, Money refundAmount) {
        return new SupplierCancelResult(supplierCode, reservationId, 
            BookingStatus.CANCELLED, refundAmount, false, null, null);
    }
    
    public static SupplierCancelResult alreadyCancelled(String supplierCode, String reservationId) {
        return new SupplierCancelResult(supplierCode, reservationId,
            BookingStatus.CANCELLED, null, true, null, null);
    }
    
    public static SupplierCancelResult failed(String supplierCode, String reservationId,
            String errorCode, String errorMessage) {
        return new SupplierCancelResult(supplierCode, reservationId,
            BookingStatus.FAILED, null, false, errorCode, errorMessage);
    }
    
    public boolean isSuccess() { return status == BookingStatus.CANCELLED; }
}
