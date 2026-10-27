package com.arcube.transferaggregator.exception;

public class SearchStateExpiredException extends RuntimeException {
    public SearchStateExpiredException() { super("Search results have expired; start a new search"); }
}
