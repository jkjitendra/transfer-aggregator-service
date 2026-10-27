package com.arcube.transferaggregator.routing;

public class RouteNotFoundException extends RuntimeException {
    public RouteNotFoundException() { super("No driving route was found for these coordinates"); }
}
