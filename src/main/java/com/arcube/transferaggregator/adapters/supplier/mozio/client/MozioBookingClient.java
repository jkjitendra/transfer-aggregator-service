package com.arcube.transferaggregator.adapters.supplier.mozio.client;

import com.arcube.transferaggregator.adapters.supplier.mozio.MozioConfig;
import com.arcube.transferaggregator.adapters.supplier.mozio.dto.MozioBookingRequest;
import com.arcube.transferaggregator.adapters.supplier.mozio.dto.MozioBookingResponse;
import com.arcube.transferaggregator.adapters.supplier.mozio.dto.MozioCancelRequest;
import com.arcube.transferaggregator.resilience.RetryHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@Component
public class MozioBookingClient {
    
    private final WebClient webClient;
    private final RetryHandler retryHandler;
    
    public MozioBookingClient(WebClient.Builder webClientBuilder, MozioConfig config, RetryHandler retryHandler) {
        this.webClient = webClientBuilder
            .baseUrl(config.getBaseUrl())
            .defaultHeader("API-KEY", config.getApiKey())
            .build();
        this.retryHandler = retryHandler;
    }
    
    public MozioBookingResponse book(MozioBookingRequest request) {
        // Initial booking request
        return retryHandler.executeWithRetry(() -> 
            webClient.post()
                .uri("/v2/reservations/")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MozioBookingResponse.class)
                .block(Duration.ofSeconds(15))
        );
    }
    
    public void cancel(String reservationId) {
        // Cancellation is idempotent, safe to retry
        retryHandler.executeWithRetry(() -> {
            webClient.delete()
                .uri("/v2/reservations/{id}/", reservationId)
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofSeconds(10));
            return null;
        });
    }
}
