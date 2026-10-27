package com.arcube.transferaggregator.routing;

import org.junit.jupiter.api.Test;
import com.arcube.transferaggregator.resilience.RetryHandler;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import com.arcube.transferaggregator.exception.RateLimitExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class OsrmRouteClientTest {
    private OsrmProperties properties() {
        OsrmProperties properties = new OsrmProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://localhost:5000");
        properties.setTimeout(Duration.ofSeconds(1));
        return properties;
    }

    @Test
    void mapsRouteAndPreservesCoordinateOrder() {
        AtomicReference<String> path = new AtomicReference<>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            path.set(request.url().getRawPath());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body("{\"code\":\"Ok\",\"routes\":[{\"distance\":1520.4,\"duration\":241.6}]}")
                .build());
        });
        var estimate = new OsrmRouteClient(builder, properties(), mock(Environment.class), new RetryHandler())
            .estimate(52.517037, 13.38886, 52.529407, 13.397634);
        assertThat(path.get()).isEqualTo("/route/v1/driving/13.388860,52.517037;13.397634,52.529407");
        assertThat(estimate.distanceMeters()).isEqualTo(1520);
        assertThat(estimate.durationSeconds()).isEqualTo(242);
        assertThat(estimate.attribution()).contains("OpenStreetMap");
    }

    @Test
    void distinguishesNoRouteFromMalformedAndUnavailableResponses() {
        var noRoute = client("{\"code\":\"NoRoute\",\"routes\":[]}");
        assertThatThrownBy(() -> noRoute.estimate(52, 13, 53, 14))
            .isInstanceOf(RouteNotFoundException.class);
        var malformed = client("{\"code\":\"Ok\",\"routes\":[]}");
        assertThatThrownBy(() -> malformed.estimate(52, 13, 53, 14))
            .isInstanceOf(RouteUnavailableException.class);
        assertThatThrownBy(() -> malformed.estimate(200, 13, 53, 14))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retriesTransientSelfHostedReadWithinDeadline() {
        AtomicInteger calls = new AtomicInteger();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            if (calls.incrementAndGet() == 1) {
                return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body("{\"code\":\"Ok\",\"routes\":[{\"distance\":100,\"duration\":20}]}")
                .build());
        });
        var estimate = new OsrmRouteClient(builder, properties(), mock(Environment.class), new RetryHandler())
            .estimate(52, 13, 53, 14);
        assertThat(estimate.distanceMeters()).isEqualTo(100);
        assertThat(calls).hasValue(2);
    }

    @Test
    void publicDemoRequiresDemoProfileAndLimitsCalls() {
        OsrmProperties settings = properties();
        settings.setBaseUrl("https://router.project-osrm.org");
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request ->
            Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                .body("{\"code\":\"Ok\",\"routes\":[{\"distance\":100,\"duration\":20}]}")
                .build()));
        Environment environment = mock(Environment.class);
        assertThatThrownBy(() -> new OsrmRouteClient(builder, settings, environment, new RetryHandler()))
            .isInstanceOf(IllegalStateException.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        OsrmRouteClient demo = new OsrmRouteClient(builder, settings, environment, new RetryHandler());
        demo.estimate(52, 13, 53, 14);
        assertThatThrownBy(() -> demo.estimate(52, 13, 53, 14))
            .isInstanceOf(RateLimitExceededException.class);
    }

    private OsrmRouteClient client(String body) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request ->
            Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json").body(body).build()));
        return new OsrmRouteClient(builder, properties(), mock(Environment.class), new RetryHandler());
    }
}
