package com.arcube.transferaggregator.observability;

import com.arcube.transferaggregator.config.ApiSecurityProperties;
import com.arcube.transferaggregator.config.TenantConfig;
import com.arcube.transferaggregator.config.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Establishes tenant identity exclusively from a validated JWT. */
public class TenantFilter extends OncePerRequestFilter {
    public static final String TENANT_HEADER = "X-Tenant-Id";

    private final TenantConfig tenantConfig;
    private final ApiSecurityProperties properties;

    public TenantFilter(TenantConfig tenantConfig, ApiSecurityProperties properties) {
        this.tenantConfig = tenantConfig;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/") || path.startsWith("/api/v1/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication)) {
            filterChain.doFilter(request, response);
            return;
        }

        String tenantId = authentication.getToken().getClaimAsString(properties.getTenantClaim());
        String requestedTenant = request.getHeader(TENANT_HEADER);
        if (tenantId == null || tenantId.isBlank() || !tenantConfig.isKnownAndEnabled(tenantId)
                || (requestedTenant != null && !requestedTenant.isBlank() && !tenantId.equals(requestedTenant.trim()))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant access denied");
            return;
        }

        try {
            TenantContext.setTenantId(tenantId);
            MDC.put("tenantId", tenantId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
        }
    }
}
