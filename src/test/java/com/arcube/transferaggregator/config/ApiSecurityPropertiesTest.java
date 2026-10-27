package com.arcube.transferaggregator.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiSecurityPropertiesTest {
    @Test
    void refusesDisabledSecurityInProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ApiSecurityProperties properties = new ApiSecurityProperties(environment);
        properties.setEnabled(false);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiresIssuerAudienceAndExplicitCorsOrigins() {
        ApiSecurityProperties properties = new ApiSecurityProperties(new MockEnvironment());
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        properties.setIssuerUri("https://issuer.example.test");
        properties.setAudience("transfer-api");
        properties.setAllowedOrigins(List.of("*"));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiresHttpsIssuerInProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ApiSecurityProperties properties = new ApiSecurityProperties(environment);
        properties.setIssuerUri("http://issuer.example.test");
        properties.setAudience("transfer-api");
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiresHttpsJwkEndpointInProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ApiSecurityProperties properties = new ApiSecurityProperties(environment);
        properties.setIssuerUri("https://issuer.example.test");
        properties.setJwkSetUri("http://internal.example.test/keys");
        properties.setAudience("transfer-api");
        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWK endpoint");
    }

    @Test
    void refusesDisabledSecurityInLocalProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        ApiSecurityProperties properties = new ApiSecurityProperties(environment);
        properties.setEnabled(false);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }
}
