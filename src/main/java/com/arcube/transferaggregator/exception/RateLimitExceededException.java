package com.arcube.transferaggregator.exception;

/**
 * Thrown when rate limit is exceeded.
 * Returns HTTP 429 Too Many Requests.
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
