package com.arcube.transferaggregator.exception;

public class TenantAccessException extends RuntimeException {
    public TenantAccessException() {
        super("Tenant access denied");
    }
}
