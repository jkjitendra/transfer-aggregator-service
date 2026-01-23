package com.arcube.transferaggregator.config;

/**
 * Thread-local holder for the current tenant context.
 * Set by TenantFilter, read by services.
 */
public class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /**
     * Get tenant ID with fallback to default.
     */
    public static String getTenantIdOrDefault(String defaultTenant) {
        String tenant = getTenantId();
        return (tenant != null && !tenant.isBlank()) ? tenant : defaultTenant;
    }
}
