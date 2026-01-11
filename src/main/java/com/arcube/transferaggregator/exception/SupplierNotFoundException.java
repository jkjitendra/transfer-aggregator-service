package com.arcube.transferaggregator.exception;

public class SupplierNotFoundException extends RuntimeException {
    public SupplierNotFoundException(String supplierCode) {
        super("Supplier not found: " + supplierCode);
    }
}
