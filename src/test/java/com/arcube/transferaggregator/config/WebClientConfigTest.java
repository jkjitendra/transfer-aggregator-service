package com.arcube.transferaggregator.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WebClientConfigTest {
    @Test
    void forwardsRequestIdToSupplierHttpCall() {
        AtomicReference<String> downstreamId = new AtomicReference<>();
        var client = new WebClientConfig().webClientBuilder()
            .exchangeFunction(request -> {
                downstreamId.set(request.headers().getFirst("X-Request-Id"));
                return Mono.just(ClientResponse.create(HttpStatus.OK).build());
            }).build();
        MDC.put("requestId", "request-123");
        try {
            client.get().uri("https://supplier.example.test/health")
                .retrieve().toBodilessEntity().block();
            assertThat(downstreamId.get()).isEqualTo("request-123");
        } finally {
            MDC.clear();
        }
    }
}
