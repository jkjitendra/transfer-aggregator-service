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
 *       enabled-suppliers: [STUB, SKYRIDE]
 *       default-currency: USD
 *     tenant-b:
 *       name: "Travel Partner B"
 *       enabled-suppliers: [SKYRIDE, SLOW_STUB]
 *       default-currency: EUR
 */
@Component
@RequiredArgsConstructor
public class TenantConfig {

    private final AggregatorProperties properties;

    /** Get an explicitly configured, enabled tenant. */
    public TenantProperties getTenant(String tenantId) {
        Map<String, TenantProperties> tenants = properties.getTenants();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = properties.getDefaultTenant();
        }
        TenantProperties tenant = tenants.get(tenantId);
        if (tenant == null || !tenant.isEnabled()) {
            throw new IllegalArgumentException("Unknown or disabled tenant");
        }
        return tenant;
    }

    public boolean isKnownAndEnabled(String tenantId) {
        TenantProperties tenant = properties.getTenants().get(tenantId);
        return tenant != null && tenant.isEnabled();
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

}
