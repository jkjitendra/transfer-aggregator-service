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
    public static class SkyRideSupplierConfig {
        private boolean enabled = true;
    }
}
