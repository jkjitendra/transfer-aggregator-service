package com.arcube.transferaggregator.resilience;

import com.arcube.transferaggregator.config.AggregatorProperties;
import com.arcube.transferaggregator.config.AggregatorProperties.ResilienceProperties;
import com.arcube.transferaggregator.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.core.env.StandardEnvironment;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterTest {
    
    @Mock
    AggregatorProperties properties;
    @Mock
    ResilienceProperties resilienceProperties;
    
    @Test
    void shouldEnforceSearchRateLimit() {
        when(properties.getResilience()).thenReturn(resilienceProperties);
        when(resilienceProperties.getSearchRateLimitPerMinute()).thenReturn(2);
        when(resilienceProperties.getPollRateLimitPerMinute()).thenReturn(10);
        
        RateLimiter limiter = new RateLimiter(properties, Optional.empty(),
            new StandardEnvironment(), Optional.empty());
        String supplier = "TEST";
        
        limiter.acquireSearchPermit(supplier); // 1
        limiter.acquireSearchPermit(supplier); // 2
        
        assertThatThrownBy(() -> limiter.acquireSearchPermit(supplier))
            .isInstanceOf(RateLimitExceededException.class);
    }
    
    @Test
    void shouldEnforcePollRateLimit() {
        when(properties.getResilience()).thenReturn(resilienceProperties);
        when(resilienceProperties.getSearchRateLimitPerMinute()).thenReturn(10);
        when(resilienceProperties.getPollRateLimitPerMinute()).thenReturn(2);
        
        RateLimiter limiter = new RateLimiter(properties, Optional.empty(),
            new StandardEnvironment(), Optional.empty());
        String searchId = "abc-123";
        
        limiter.acquirePollPermit(searchId); // 1
        limiter.acquirePollPermit(searchId); // 2
        
        assertThatThrownBy(() -> limiter.acquirePollPermit(searchId))
            .isInstanceOf(RateLimitExceededException.class);
            
        // Different search ID should be fine
        assertThatCode(() -> limiter.acquirePollPermit("xyz-789"))
            .doesNotThrowAnyException();
    }
}
