package com.arcube.transferaggregator.observability;

import com.arcube.transferaggregator.config.AggregatorProperties;
import com.arcube.transferaggregator.ports.SupplierRegistry;
import com.arcube.transferaggregator.ports.TransferSupplier;
import com.arcube.transferaggregator.resilience.SupplierBulkhead;
import com.arcube.transferaggregator.resilience.SupplierCircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Fixed supplier roster provides bounded circuit and bulkhead gauges. */
@Configuration
public class ResilienceMetrics {
    @Bean
    MeterFilter supplierMetricLimit() {
        return MeterFilter.maximumAllowableTags("transfer", "supplier", 20, MeterFilter.deny());
    }

    @Bean
    MeterFilter legacySupplierMetricLimit() {
        return MeterFilter.maximumAllowableTags("aggregator", "supplier", 20, MeterFilter.deny());
    }

    @Bean
    SmartInitializingSingleton resilienceGaugeRegistration(MeterRegistry registry, SupplierRegistry suppliers,
                                                            SupplierBulkhead bulkhead, SupplierCircuitBreaker circuit,
                                                            AggregatorProperties properties) {
        return () -> {
            Gauge.builder("transfer.bulkhead.available", bulkhead, SupplierBulkhead::availablePermits)
                .register(registry);
            int capacity = properties.getResilience().getMaxConcurrentCalls();
            Gauge.builder("transfer.bulkhead.saturation", bulkhead,
                value -> 1.0 - ((double) value.availablePermits() / capacity)).register(registry);
            for (TransferSupplier supplier : suppliers.getEnabledSuppliers()) {
                String code = supplier.getSupplierCode();
                Gauge.builder("transfer.supplier.circuit.state", circuit, value -> {
                    CircuitBreaker.State state = value.getState(code);
                    return state == CircuitBreaker.State.OPEN ? 2 :
                        state == CircuitBreaker.State.HALF_OPEN ? 1 : 0;
                }).tag("supplier", code).register(registry);
            }
        };
    }
}
