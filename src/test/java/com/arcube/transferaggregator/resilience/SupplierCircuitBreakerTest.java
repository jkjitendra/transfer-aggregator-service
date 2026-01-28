package com.arcube.transferaggregator.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierCircuitBreakerTest {

    @Test
    void executeFallsBackOnException() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        SupplierCircuitBreaker breaker = new SupplierCircuitBreaker(registry);

        String result = breaker.execute("S1",
            () -> { throw new RuntimeException("boom"); },
            () -> "fallback");

        assertThat(result).isEqualTo("fallback");
    }

    @Test
    void getStateAndIsOpen() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        SupplierCircuitBreaker breaker = new SupplierCircuitBreaker(registry);

        CircuitBreaker cb = registry.circuitBreaker("S2");
        assertThat(breaker.isOpen("S2")).isFalse();
        assertThat(breaker.getState("S2")).isEqualTo(cb.getState());

        cb.transitionToOpenState();
        assertThat(breaker.isOpen("S2")).isTrue();
    }
}
