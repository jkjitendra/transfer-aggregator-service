package com.arcube.transferaggregator.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import com.arcube.transferaggregator.config.AggregatorProperties.TenantProperties;

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
@Component
@RequiredArgsConstructor
public class TenantConfig {

    private final AggregatorProperties properties;

    /**
     * Get tenant config, falling back to default if not found.
     */
    public TenantProperties getTenant(String tenantId) {
        Map<String, TenantProperties> tenants = properties.getTenants();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = properties.getDefaultTenant();
        }
        return tenants.getOrDefault(tenantId, getDefaultTenantConfig());
    }

    public String getDefaultTenant() {
        return properties.getDefaultTenant();
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
