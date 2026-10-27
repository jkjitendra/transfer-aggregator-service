package com.arcube.transferaggregator.observability;

import com.arcube.transferaggregator.config.ApiSecurityProperties;
import com.arcube.transferaggregator.config.TenantConfig;
import com.arcube.transferaggregator.config.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantFilterTest {
    private final TenantConfig tenantConfig = mock(TenantConfig.class);
    private final ApiSecurityProperties properties = new ApiSecurityProperties(new MockEnvironment());
    private final TenantFilter filter = new TenantFilter(tenantConfig, properties);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void takesTenantFromValidatedJwtAndClearsContext() throws Exception {
        authenticate("tenant-a");
        when(tenantConfig.isKnownAndEnabled("tenant-a")).thenReturn(true);
        MockHttpServletRequest request = apiRequest();
        request.addHeader(TenantFilter.TENANT_HEADER, "tenant-a");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
            assertThat(TenantContext.getTenantId()).isEqualTo("tenant-a"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void rejectsConflictingHeader() throws Exception {
        authenticate("tenant-a");
        when(tenantConfig.isKnownAndEnabled("tenant-a")).thenReturn(true);
        MockHttpServletRequest request = apiRequest();
        request.addHeader(TenantFilter.TENANT_HEADER, "tenant-b");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> called.set(true));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(called).isFalse();
    }

    @Test
    void rejectsUnknownOrDisabledTenant() throws Exception {
        authenticate("missing");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        filter.doFilter(apiRequest(), response, (req, res) -> called.set(true));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(called).isFalse();
    }

    @Test
    void anonymousRequestNeverTrustsTenantHeader() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.addHeader(TenantFilter.TENANT_HEADER, "tenant-a");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
            assertThat(TenantContext.getTenantId()).isNull());
    }

    private MockHttpServletRequest apiRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/transfers/search");
        return request;
    }

    private void authenticate(String tenant) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none")
            .subject("caller").claim("tenant_id", tenant).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
