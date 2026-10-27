package com.arcube.transferaggregator.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "management.tracing.sampling.probability=1.0")
@AutoConfigureObservability
@ActiveProfiles("test")
class TracingPropagationTest {
    @Autowired WebClient.Builder builder;
    @Autowired ObservationRegistry observations;

    @Test
    void autoConfiguredClientPropagatesW3cTraceAndRequestId() {
        AtomicReference<String> traceparent = new AtomicReference<>();
        AtomicReference<String> requestId = new AtomicReference<>();
        WebClient client = builder.clone().exchangeFunction(request -> {
            traceparent.set(request.headers().getFirst("traceparent"));
            requestId.set(request.headers().getFirst("X-Request-Id"));
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        }).build();

        MDC.put("requestId", "trace-test-123");
        try {
            Observation.createNotStarted("test.parent", observations).observe(() ->
                client.get().uri("https://supplier.example.test/health")
                    .retrieve().toBodilessEntity().block());
        } finally {
            MDC.clear();
        }
        assertThat(traceparent.get()).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]");
        assertThat(requestId.get()).isEqualTo("trace-test-123");
    }
}
