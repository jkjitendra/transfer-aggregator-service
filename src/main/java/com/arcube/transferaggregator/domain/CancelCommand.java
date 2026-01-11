package com.arcube.transferaggregator.domain;

// Command for cancel operation
public record CancelCommand(String reservationId, String supplierCode) {
    public static CancelCommand of(String reservationId, String supplierCode) {
        return new CancelCommand(reservationId, supplierCode);
    }
}
