package com.arcube.transferaggregator.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenPropertiesTest {
    @Test
    void rejectsMissingWeakAndRetiredSecrets() {
        TokenProperties properties = new TokenProperties();
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        properties.setSecret("short");
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        properties.setSecret("57fd0a434be5ba1dc468f69ee204c70328ceb8cac56d6f3e70cd39c49ce5f7d4");
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        properties.setSecret("demo-only-key-do-not-use-in-production-1234567890");
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAmbiguousRotationKey() {
        TokenProperties properties = new TokenProperties();
        properties.setSecret("01234567890123456789012345678901");
        properties.setPreviousKeys(java.util.Map.of("v1", "abcdefghijklmnopqrstuvwxyzABCDEF0123456789"));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsDemoKeyOnlyInDemoProfile() {
        TokenProperties properties = new TokenProperties();
        properties.setSecret("demo-only-key-do-not-use-in-production-1234567890");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("demo");
        properties.setEnvironment(environment);
        properties.validate();
    }
}
