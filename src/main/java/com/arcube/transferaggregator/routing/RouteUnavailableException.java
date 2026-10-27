package com.arcube.transferaggregator.routing;

public class RouteUnavailableException extends RuntimeException {
    public RouteUnavailableException(Throwable cause) { super("Route estimation is temporarily unavailable", cause); }
}
