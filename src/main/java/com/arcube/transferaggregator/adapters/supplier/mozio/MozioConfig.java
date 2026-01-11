package com.arcube.transferaggregator.adapters.supplier.mozio;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// Mozio-specific configuration properties
@Data
@Configuration
@ConfigurationProperties(prefix = "transfer.aggregator.suppliers.mozio")
public class MozioConfig {
    private String baseUrl = "https://api-testing.mozio.com";
    private String apiKey;
    private int pollIntervalMs = 2000;
    private int searchValidityMinutes = 20;
    private int initialRequestTimeoutSeconds = 10;
    private int pollResponseTimeoutSeconds = 5;
}
