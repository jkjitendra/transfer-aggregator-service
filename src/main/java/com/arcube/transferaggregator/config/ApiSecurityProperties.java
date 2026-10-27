package com.arcube.transferaggregator.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.ToString;
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
    private String jwkSetUri;
    private String audience;
    private String tenantClaim = "tenant_id";
    private String rolesClaim = "roles";
    private List<String> allowedOrigins = new ArrayList<>();

    @ToString.Exclude
    private final Environment environment;

    public ApiSecurityProperties(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        if (!enabled && production) {
            throw new IllegalStateException("API security cannot be disabled in the prod profile");
        }
        if (!enabled && !environment.acceptsProfiles(Profiles.of("test"))) {
            throw new IllegalStateException("API security can only be disabled in the test profile");
        }
        if (enabled && (isBlank(issuerUri) || isBlank(audience))) {
            throw new IllegalStateException("OIDC_ISSUER_URI and OIDC_AUDIENCE are required when API security is enabled");
        }
        if (production && !issuerUri.startsWith("https://")) {
            throw new IllegalStateException("Production OIDC issuer must use HTTPS");
        }
        if (production && !isBlank(jwkSetUri) && !jwkSetUri.startsWith("https://")) {
            throw new IllegalStateException("Production OIDC JWK endpoint must use HTTPS");
        }
        if (allowedOrigins.stream().anyMatch(origin -> "*".equals(origin.trim()))) {
            throw new IllegalStateException("Wildcard CORS origins are not allowed");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
