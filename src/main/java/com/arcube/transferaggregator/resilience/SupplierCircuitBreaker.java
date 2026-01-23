package com.arcube.transferaggregator.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Slf4j
@Component
public class SupplierCircuitBreaker {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public SupplierCircuitBreaker(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        log.info("SupplierCircuitBreaker initialized with registry");
    }

    public <T> T execute(String supplierCode, Supplier<T> supplier, Supplier<T> fallback) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(supplierCode);
        
        try {
            return CircuitBreaker.decorateSupplier(circuitBreaker, supplier).get();
        } catch (Exception e) {
            log.warn("Circuit breaker {} triggered fallback: {} - State: {}", 
                supplierCode, e.getMessage(), circuitBreaker.getState());
            return fallback.get();
        }
    }

    public CircuitBreaker.State getState(String supplierCode) {
        return circuitBreakerRegistry.circuitBreaker(supplierCode).getState();
    }

    public boolean isOpen(String supplierCode) {
        return circuitBreakerRegistry.circuitBreaker(supplierCode).getState() == CircuitBreaker.State.OPEN;
    }
}
