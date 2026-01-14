package com.arcube.transferaggregator.ports;

import com.arcube.transferaggregator.domain.Offer;
import lombok.Builder;

import java.util.List;

@Builder
public record SupplierReservationChangeSearchResult(
    String searchId,
    List<Offer> offers,
    boolean complete,
    String errorMessage,
    
    // Original reservation info for reference
    String oldReservationId,
    String supplierCode
) {
    public static SupplierReservationChangeSearchResult success(
            String searchId, List<Offer> offers, String oldReservationId, String supplierCode) {
        return new SupplierReservationChangeSearchResult(searchId, offers, true, null, 
            oldReservationId, supplierCode);
    }

    public static SupplierReservationChangeSearchResult error(String errorMessage, 
            String oldReservationId, String supplierCode) {
        return new SupplierReservationChangeSearchResult(null, List.of(), false, errorMessage,
            oldReservationId, supplierCode);
    }
}
