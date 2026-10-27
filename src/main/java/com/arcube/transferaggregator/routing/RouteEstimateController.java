package com.arcube.transferaggregator.routing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Optional route estimate API for visualizing a transfer journey. */
@RestController
@RequestMapping("/api/v1/routes")
@ConditionalOnProperty(name = "routing.osrm.enabled", havingValue = "true")
public class RouteEstimateController {
    private final OsrmRouteClient routes;

    public RouteEstimateController(OsrmRouteClient routes) { this.routes = routes; }

    @GetMapping("/estimate")
    public OsrmRouteClient.RouteEstimate estimate(
            @RequestParam double pickupLatitude, @RequestParam double pickupLongitude,
            @RequestParam double dropoffLatitude, @RequestParam double dropoffLongitude) {
        return routes.estimate(pickupLatitude, pickupLongitude, dropoffLatitude, dropoffLongitude);
    }
}
