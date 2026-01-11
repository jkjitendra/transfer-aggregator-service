package com.arcube.transferaggregator.observability;

import com.arcube.transferaggregator.adapters.supplier.mozio.MozioConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Health indicator for Mozio API connectivity.
 * Verifies the API is reachable and credentials are valid.
 */
@Slf4j
@Component("mozioHealth")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "transfer.aggregator.mode", havingValue = "real")
public class MozioHealthIndicator implements HealthIndicator {
    
    private final MozioConfig config;
    private final WebClient.Builder webClientBuilder;
    
    @Override
    public Health health() {
        try {
            // Simple connectivity check - just hit the API root or a known endpoint
            WebClient client = webClientBuilder
                .baseUrl(config.getBaseUrl())
                .defaultHeader("API-KEY", config.getApiKey())
                .build();
            
            // Try to hit Mozio health API
            var response = client.get()
                .uri("/v2/health")  // Root or health endpoint
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(5))
                .block();
            
            if (response != null && response.getStatusCode().is2xxSuccessful()) {
                return Health.up()
                    .withDetail("baseUrl", config.getBaseUrl())
                    .withDetail("status", "Connected")
                    .build();
            } else {
                return Health.down()
                    .withDetail("baseUrl", config.getBaseUrl())
                    .withDetail("status", "Unexpected response")
                    .build();
            }
            
        } catch (Exception e) {
            log.warn("Mozio health check failed: {}", e.getMessage());
            return Health.down()
                .withDetail("baseUrl", config.getBaseUrl())
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
