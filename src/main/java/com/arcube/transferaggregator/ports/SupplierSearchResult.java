package com.arcube.transferaggregator.ports;

import com.arcube.transferaggregator.domain.Offer;

import java.util.List;

/** Result of a supplier search operation */
public record SupplierSearchResult(
    String supplierCode,
    String searchId,
    List<Offer> offers,
    boolean complete,
    boolean timedOut,
    int pollCount,
    String errorMessage
) {
    public static SupplierSearchResult success(String supplierCode, String searchId, 
            List<Offer> offers, boolean complete, int pollCount) {
        return new SupplierSearchResult(supplierCode, searchId, offers, complete, false, pollCount, null);
    }

    public static SupplierSearchResult error(String supplierCode, String errorMessage) {
        return new SupplierSearchResult(supplierCode, null, List.of(), false, false, 0, errorMessage);
    }
    
    public boolean isSuccess() {
        return errorMessage == null && offers != null && !offers.isEmpty();
    }
}
