package com.arcube.transferaggregator.exception;

/**
 * Thrown when the service is too busy to accept new requests.
 * Returns HTTP 503 Service Unavailable.
 */
public class ServiceBusyException extends RuntimeException {
    public ServiceBusyException(String message) {
        super(message);
    }
}
