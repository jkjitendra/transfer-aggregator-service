package com.arcube.transferaggregator.exception;

public class SearchStateNotFoundException extends RuntimeException {
    public SearchStateNotFoundException() { super("Search ID was not found"); }
}
