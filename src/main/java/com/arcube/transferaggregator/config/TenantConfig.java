package com.arcube.transferaggregator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-tenant configuration for filtering suppliers per tenant.
 * 
 * Configuration in application.yml:
 * aggregator:
 *   tenants:
 *     tenant-a:
 *       name: "Travel Partner A"
 *       enabled-suppliers: [STUB, MOZIO]
 *       default-currency: USD
 *     tenant-b:
 *       name: "Travel Partner B"
 *       enabled-suppliers: [SKYRIDE, MOZIO]
 *       default-currency: EUR
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "transfer.aggregator")
public class TenantConfig {

    private Map<String, TenantProperties> tenants = new HashMap<>();
    
    // Default tenant for requests without x-tenant-id header
    private String defaultTenant = "default";

    @Data
    public static class TenantProperties {
        private String name;
        private List<String> enabledSuppliers;   // Which suppliers this tenant can use
        private String defaultCurrency = "USD";
        private boolean enabled = true;
        private Integer maxResultsPerSupplier;   // Optional limit per supplier
        private Map<String, String> metadata;    // Custom tenant metadata
    }

    /**
     * Get tenant config, falling back to default if not found.
     */
    public TenantProperties getTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = defaultTenant;
        }
        return tenants.getOrDefault(tenantId, getDefaultTenantConfig());
    }

    /**
     * Check if a supplier is enabled for a tenant.
     */
    public boolean isSupplierEnabled(String tenantId, String supplierCode) {
        TenantProperties tenant = getTenant(tenantId);
        if (tenant.getEnabledSuppliers() == null || tenant.getEnabledSuppliers().isEmpty()) {
            return true;  // No restrictions = all suppliers enabled
        }
        return tenant.getEnabledSuppliers().contains(supplierCode);
    }

    private TenantProperties getDefaultTenantConfig() {
        TenantProperties defaultConfig = new TenantProperties();
        defaultConfig.setName("Default Tenant");
        defaultConfig.setEnabled(true);
        // Empty enabledSuppliers = all suppliers allowed
        return defaultConfig;
    }
}
