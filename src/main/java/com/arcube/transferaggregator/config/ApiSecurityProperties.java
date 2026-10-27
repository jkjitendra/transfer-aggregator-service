package com.arcube.transferaggregator.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "security.api")
public class ApiSecurityProperties {
    private boolean enabled = true;
    private String issuerUri;
    private String audience;
    private String tenantClaim = "tenant_id";
    private String rolesClaim = "roles";
    private List<String> allowedOrigins = new ArrayList<>();

    @Autowired
    private Environment environment;

    @PostConstruct
    public void validate() {
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        if (production && environment.acceptsProfiles(Profiles.of("demo"))) {
            throw new IllegalStateException("demo and prod profiles cannot be combined");
        }
        if (!enabled && production) {
            throw new IllegalStateException("API security cannot be disabled in the prod profile");
        }
        if (!enabled && !environment.acceptsProfiles(Profiles.of("demo", "test"))) {
            throw new IllegalStateException("API security can only be disabled in demo or test profiles");
        }
        if (enabled && (isBlank(issuerUri) || isBlank(audience))) {
            throw new IllegalStateException("OIDC_ISSUER_URI and OIDC_AUDIENCE are required when API security is enabled");
        }
        if (production && !issuerUri.startsWith("https://")) {
            throw new IllegalStateException("Production OIDC issuer must use HTTPS");
        }
        if (allowedOrigins.stream().anyMatch(origin -> "*".equals(origin.trim()))) {
            throw new IllegalStateException("Wildcard CORS origins are not allowed");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
