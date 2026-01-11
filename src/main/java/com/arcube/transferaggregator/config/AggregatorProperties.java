package com.arcube.transferaggregator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "transfer.aggregator")
public class AggregatorProperties {
    
    private String mode = "stub";
    
    private int globalTimeoutSeconds = 10;
    
    private Map<String, SupplierProperties> suppliers = new HashMap<>();
  
    private ResilienceProperties resilience = new ResilienceProperties();

    public boolean isStubMode() {
        return "stub".equalsIgnoreCase(mode);
    }
    
    @Data
    public static class SupplierProperties {
        private boolean enabled = true;
        private String baseUrl;
        private String apiKey;
        private int pollIntervalMs = 1500;
        private int maxPollAttempts = 10;
        private int searchValidityMinutes = 20;
    }
    
    @Data
    public static class ResilienceProperties {
        private int maxConcurrentCalls = 50;
        private int searchRateLimitPerMinute = 80;
        private int pollRateLimitPerMinute = 25;
    }
}
