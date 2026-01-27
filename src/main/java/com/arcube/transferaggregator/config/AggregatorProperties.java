package com.arcube.transferaggregator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "transfer.aggregator")
public class AggregatorProperties {
    
    private String mode = "stub";
    
    private int globalTimeoutSeconds = 10;
    
    private Map<String, SupplierProperties> suppliers = new HashMap<>();
  
    private ResilienceProperties resilience = new ResilienceProperties();

    // Multi-tenant configuration
    private String defaultTenant = "default";

    private Map<String, TenantProperties> tenants = new HashMap<>();

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

    @Data
    public static class TenantProperties {
        private String name;
        private List<String> enabledSuppliers;   // Which suppliers this tenant can use
        private String defaultCurrency = "USD";
        private boolean enabled = true;
        private Integer maxResultsPerSupplier;   // Optional limit per supplier
        private Map<String, String> metadata;    // Custom tenant metadata
    }
}
