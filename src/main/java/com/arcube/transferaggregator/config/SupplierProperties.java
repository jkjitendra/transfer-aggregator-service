package com.arcube.transferaggregator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configuration properties for enabling/disabling suppliers at runtime */
@Data
@Configuration
@ConfigurationProperties(prefix = "transfer.aggregator.suppliers")
public class SupplierProperties {

    private StubSupplierConfig stub = new StubSupplierConfig();
    private SlowStubSupplierConfig slowStub = new SlowStubSupplierConfig();
    private MozioSupplierConfig mozio = new MozioSupplierConfig();
    private SkyRideSupplierConfig skyride = new SkyRideSupplierConfig();

    @Data
    public static class StubSupplierConfig {
        private boolean enabled = true;
    }

    @Data
    public static class SlowStubSupplierConfig {
        private boolean enabled = false;
    }

    @Data
    public static class MozioSupplierConfig {
        private boolean enabled = true;
        private String baseUrl = "https://api-testing.mozio.com";
        private String apiKey;
        private int pollIntervalMs = 2000;
        private int maxPollAttempts = 5;
        private int searchValidityMinutes = 20;
        private int initialRequestTimeoutSeconds = 10;
        private int pollResponseTimeoutSeconds = 5;
    }

    @Data
    public static class SkyRideSupplierConfig {
        private boolean enabled = true;
    }
}
