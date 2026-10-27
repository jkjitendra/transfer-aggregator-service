package com.arcube.transferaggregator.routing;

import com.arcube.transferaggregator.exception.RateLimitExceededException;
import com.arcube.transferaggregator.resilience.RetryHandler;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/** Read-only route estimates; this API does not create transfer reservations. */
@Service
@ConditionalOnProperty(name = "routing.osrm.enabled", havingValue = "true")
public class OsrmRouteClient {
    private final WebClient client;
    private final OsrmProperties properties;
    private final RetryHandler retryHandler;
    private final String baseUrl;
    private final boolean publicDemo;
    private final AtomicLong nextPublicCall = new AtomicLong(Long.MIN_VALUE);

    public record RouteEstimate(long distanceMeters, long durationSeconds, String attribution) {}

    public OsrmRouteClient(WebClient.Builder builder, OsrmProperties properties,
                           Environment environment, RetryHandler retryHandler) {
        if (properties.getTimeout() == null || properties.getTimeout().isNegative()
                || properties.getTimeout().isZero() || properties.getTimeout().toSeconds() > 10) {
            throw new IllegalStateException("OSRM timeout must be between 1 ms and 10 seconds");
        }
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || !(baseUrl.startsWith("https://") || baseUrl.startsWith("http://localhost:")
                || baseUrl.startsWith("http://127.0.0.1:"))) {
            throw new IllegalStateException("OSRM URL must use HTTPS or local HTTP");
        }
        this.publicDemo = baseUrl.contains("router.project-osrm.org");
        if (publicDemo && !environment.acceptsProfiles(Profiles.of("demo"))) {
            throw new IllegalStateException("The public OSRM demo is allowed only in demo profile");
        }
        this.properties = properties;
        this.retryHandler = retryHandler;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.client = builder.baseUrl(baseUrl).build();
    }

    public RouteEstimate estimate(double pickupLatitude, double pickupLongitude,
                                  double dropoffLatitude, double dropoffLongitude) {
        validate(pickupLatitude, pickupLongitude);
        validate(dropoffLatitude, dropoffLongitude);
        if (publicDemo) acquireDemoPermit();
        String coordinates = String.format(Locale.ROOT, "%.6f,%.6f;%.6f,%.6f",
            pickupLongitude, pickupLatitude, dropoffLongitude, dropoffLatitude);
        try {
            Instant deadline = Instant.now().plus(properties.getTimeout());
            if (publicDemo) return fetchEstimate(coordinates, deadline);
            return retryHandler.executeReadWithinDeadline(
                () -> fetchEstimate(coordinates, deadline), deadline, 2);
        } catch (RouteNotFoundException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RouteUnavailableException(exception);
        }
    }

    private RouteEstimate fetchEstimate(String coordinates, Instant deadline) {
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isNegative() || remaining.isZero()) {
            throw new IllegalStateException("Route estimate deadline exceeded");
        }
        JsonNode body = client.get().uri(URI.create(baseUrl +
                "/route/v1/driving/" + coordinates + "?overview=false"))
            .retrieve().bodyToMono(JsonNode.class).block(remaining);
        if (body == null) throw new IllegalStateException("Empty route response");
        if ("NoRoute".equals(body.path("code").asText())) throw new RouteNotFoundException();
        JsonNode route = body.path("routes").path(0);
        double distance = route.path("distance").asDouble(Double.NaN);
        double duration = route.path("duration").asDouble(Double.NaN);
        if (!"Ok".equals(body.path("code").asText()) || !Double.isFinite(distance)
                || !Double.isFinite(duration) || distance < 0 || duration < 0) {
            throw new IllegalStateException("Malformed route response");
        }
        return new RouteEstimate(Math.round(distance), Math.round(duration), "OpenStreetMap / OSRM");
    }

    private void validate(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Coordinates must be finite latitude/longitude values");
        }
    }

    private void acquireDemoPermit() {
        long now = System.nanoTime();
        long next = nextPublicCall.get();
        if (now < next || !nextPublicCall.compareAndSet(next, now + 1_000_000_000L)) {
            throw new RateLimitExceededException("Public routing demo permits one request per second");
        }
    }
}
