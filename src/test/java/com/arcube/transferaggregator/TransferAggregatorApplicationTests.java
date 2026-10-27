package com.arcube.transferaggregator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "security.api.enabled=false",
    "security.token.secret=test-only-key-not-for-production-123456789012345",
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
    "spring.flyway.enabled=false",
    "management.otlp.metrics.export.enabled=false",
    "transfer.aggregator.tenants.default.enabled=true"
})
@ActiveProfiles("test")
class TransferAggregatorApplicationTests {
    @Test
    void contextLoads() {
    }

    @Test
    void requiresExactlyOneRuntimeProfile() {
        MockEnvironment environment = new MockEnvironment();
        assertThatThrownBy(() -> TransferAggregatorApplication.validateRuntimeProfile(environment))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SPRING_PROFILES_ACTIVE");

        environment.setActiveProfiles("local");
        TransferAggregatorApplication.validateRuntimeProfile(environment);

        environment.setActiveProfiles("prod");
        TransferAggregatorApplication.validateRuntimeProfile(environment);

        environment.setActiveProfiles("local", "prod");
        assertThatThrownBy(() -> TransferAggregatorApplication.validateRuntimeProfile(environment))
            .isInstanceOf(IllegalStateException.class);

        environment.setActiveProfiles("test");
        assertThatThrownBy(() -> TransferAggregatorApplication.validateRuntimeProfile(environment))
            .isInstanceOf(IllegalStateException.class);
    }

}
