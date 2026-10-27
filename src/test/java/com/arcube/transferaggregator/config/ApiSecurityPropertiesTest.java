package com.arcube.transferaggregator.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiSecurityPropertiesTest {
    @Test
    void refusesDisabledSecurityInProduction() {
        ApiSecurityProperties properties = new ApiSecurityProperties();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        properties.setEnvironment(environment);
        properties.setEnabled(false);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiresIssuerAudienceAndExplicitCorsOrigins() {
        ApiSecurityProperties properties = new ApiSecurityProperties();
        properties.setEnvironment(new MockEnvironment());
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        properties.setIssuerUri("https://issuer.example.test");
        properties.setAudience("transfer-api");
        properties.setAllowedOrigins(List.of("*"));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiresHttpsIssuerInProduction() {
        ApiSecurityProperties properties = new ApiSecurityProperties();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        properties.setEnvironment(environment);
        properties.setIssuerUri("http://issuer.example.test");
        properties.setAudience("transfer-api");
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesCombinedDemoAndProductionProfiles() {
        ApiSecurityProperties properties = new ApiSecurityProperties();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("demo", "prod");
        properties.setEnvironment(environment);
        properties.setIssuerUri("https://issuer.example.test");
        properties.setAudience("transfer-api");
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }
}
