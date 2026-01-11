package com.arcube.transferaggregator.exception;

public class CancellationNotAllowedException extends RuntimeException {
    public CancellationNotAllowedException(String bookingId, String reason) {
        super("Cannot cancel booking " + bookingId + ": " + reason);
    }
}
