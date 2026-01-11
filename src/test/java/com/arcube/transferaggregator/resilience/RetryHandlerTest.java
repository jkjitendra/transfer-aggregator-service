package com.arcube.transferaggregator.resilience;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryHandlerTest {
    
    @Test
    void shouldRetryOnTransientFailure() {
        RetryHandler handler = new RetryHandler();
        final int[] attempts = {0};
        
        String result = handler.executeWithRetry(() -> {
            attempts[0]++;
            if (attempts[0] < 2) {
                throw new RuntimeException("SocketTimeoutException"); // Simulated retryable name match
            }
            return "success";
        }, 2);
        
        assertThat(result).isEqualTo("success");
        assertThat(attempts[0]).isEqualTo(2);
    }
    
    @Test
    void shouldFailAfterMaxRetries() {
        RetryHandler handler = new RetryHandler();
        
        assertThatThrownBy(() -> handler.executeWithRetry(() -> {
            throw new RuntimeException("Connection refused");
        }, 2))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Connection refused");
    }
    
    @Test
    void shouldNotRetryNonTransientErrors() {
        RetryHandler handler = new RetryHandler();
        final int[] attempts = {0};
        
        assertThatThrownBy(() -> handler.executeWithRetry(() -> {
            attempts[0]++;
            throw new IllegalArgumentException("Bad argument");
        }, 2))
            .isInstanceOf(IllegalArgumentException.class);
            
        assertThat(attempts[0]).isEqualTo(1); // No retry
    }
}
