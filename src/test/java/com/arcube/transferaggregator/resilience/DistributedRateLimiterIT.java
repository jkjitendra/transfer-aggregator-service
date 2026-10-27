package com.arcube.transferaggregator.resilience;

import com.arcube.transferaggregator.config.AggregatorProperties;
import com.arcube.transferaggregator.config.TenantContext;
import com.arcube.transferaggregator.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Explicit local Redis test of limits shared by two independent service instances. */
@EnabledIfEnvironmentVariable(named = "TRANSFER_TEST_REDIS_HOST", matches = ".+")
class DistributedRateLimiterIT {
    @Test
    void twoInstancesEnforceOneTenantSupplierBudget() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
            System.getenv("TRANSFER_TEST_REDIS_HOST"), 6379);
        factory.afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            redis.afterPropertiesSet();
            Environment environment = mock(Environment.class);
            when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
            AggregatorProperties properties = new AggregatorProperties();
            properties.getResilience().setSearchRateLimitPerMinute(3);
            RateLimiter first = limiter(properties, redis, environment);
            RateLimiter second = limiter(properties, redis, environment);
            TenantContext.setTenantId("rate-test-" + UUID.randomUUID());
            try {
                first.acquireSearchPermit("STUB");
                second.acquireSearchPermit("STUB");
                first.acquireSearchPermit("STUB");
                assertThatThrownBy(() -> second.acquireSearchPermit("STUB"))
                    .isInstanceOf(RateLimitExceededException.class);
            } finally {
                TenantContext.clear();
            }
        } finally {
            factory.destroy();
        }
    }

    private RateLimiter limiter(AggregatorProperties properties, StringRedisTemplate redis,
                                Environment environment) {
        RateLimiter limiter = new RateLimiter(properties);
        ReflectionTestUtils.setField(limiter, "redis", redis);
        ReflectionTestUtils.setField(limiter, "environment", environment);
        return limiter;
    }
}
