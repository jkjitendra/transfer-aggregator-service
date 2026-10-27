package com.arcube.transferaggregator;

import com.arcube.transferaggregator.service.AlertingService;
import com.arcube.transferaggregator.service.PricingService;
import com.arcube.transferaggregator.config.TenantConfig;
import com.arcube.transferaggregator.config.AggregatorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "security.api.enabled=true",
    "security.api.issuer-uri=https://issuer.example.test",
    "security.api.audience=transfer-api",
    "security.token.secret=test-only-key-not-for-production-123456789012345",
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
    "spring.flyway.enabled=false",
    "management.otlp.metrics.export.enabled=false",
    "transfer.aggregator.tenants.default.enabled=true",
    "transfer.aggregator.tenants.tenant-a.enabled=true",
    "transfer.aggregator.tenants.tenant-b.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityBoundaryTest {
    @Autowired MockMvc mvc;
    @Autowired TenantConfig tenantConfig;
    @Autowired AggregatorProperties aggregatorProperties;
    @Autowired Environment environment;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean PricingService pricingService;
    @MockitoBean AlertingService alertingService;

    @BeforeEach
    void setUp() {
        assertThat(tenantConfig.isKnownAndEnabled("tenant-a"))
            .as("Configured tenant keys: %s, raw tenant enabled: %s",
                aggregatorProperties.getTenants().keySet(),
                environment.getProperty("transfer.aggregator.tenants.tenant-a.enabled")).isTrue();
        when(decoder.decode(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            String[] fields = token.split("\\.", -1);
            String role = fields[0];
            String tenant = fields.length > 1 ? fields[1] : "tenant-a";
            Jwt.Builder builder = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("test-user")
                .issuer("https://issuer.example.test")
                .audience(List.of("transfer-api"))
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("roles", List.of(role));
            if (!tenant.isEmpty()) builder.claim("tenant_id", tenant);
            return builder.build();
        });
        when(pricingService.getAvailableAmenities(anyString())).thenReturn(List.of());
        when(alertingService.getDlqStatus()).thenReturn(new AlertingService.AlertStatus(
            AlertingService.AlertLevel.OK, 0, 10, 50));
    }

    @Test
    void rejectsAnonymousAndCustomerAdminAccess() throws Exception {
        mvc.perform(get("/api/v1/pricing/offer/amenities")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/pricing/offer/amenities").header("Authorization", "Bearer CUSTOMER.tenant-a"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/alerts/errors").header("Authorization", "Bearer CUSTOMER.tenant-a"))
            .andExpect(status().isForbidden());
    }

    @Test
    void separatesAdminReadAndWriteRoles() throws Exception {
        mvc.perform(get("/api/v1/admin/alerts/errors").header("Authorization", "Bearer SUPPORT.tenant-a"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/alerts/check").header("Authorization", "Bearer SUPPORT.tenant-a"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/alerts/check").header("Authorization", "Bearer OPERATOR.tenant-a"))
            .andExpect(status().isOk());
    }

    @Test
    void bindsTenantToJwtAndRejectsUnknownOrMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/pricing/offer/amenities")
                .header("Authorization", "Bearer CUSTOMER.tenant-a")
                .header("X-Tenant-Id", "tenant-b"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/pricing/offer/amenities")
                .header("Authorization", "Bearer CUSTOMER.unknown"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/pricing/offer/amenities")
                .header("Authorization", "Bearer CUSTOMER."))
            .andExpect(status().isForbidden());
    }

    @Test
    void protectsDocsAndMetricsAndOmitsSimulationOutsideDemo() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(200, 503));
        mvc.perform(get("/openapi.yaml")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer CUSTOMER.tenant-a"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/alerts/dlq/simulate")
                .header("Authorization", "Bearer OPERATOR.tenant-a"))
            .andExpect(status().isNotFound());
    }
}
