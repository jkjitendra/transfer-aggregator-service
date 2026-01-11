package com.arcube.transferaggregator.exception;

/**
 * Thrown when supplier times out.
 * Returns HTTP 504 Gateway Timeout.
 */
public class SupplierTimeoutException extends RuntimeException {
    private final String supplierCode;
    
    public SupplierTimeoutException(String supplierCode, String message) {
        super(message);
        this.supplierCode = supplierCode;
    }
    
    public String getSupplierCode() {
        return supplierCode;
    }
}
