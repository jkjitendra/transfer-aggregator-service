package com.arcube.transferaggregator.exception;

public class SearchStateUnavailableException extends RuntimeException {
    public SearchStateUnavailableException(Throwable cause) { super("Search state is temporarily unavailable", cause); }
}
