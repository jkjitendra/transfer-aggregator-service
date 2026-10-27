package com.arcube.transferaggregator.config;

import com.arcube.transferaggregator.ports.LiveTransferSupplier;
import com.arcube.transferaggregator.ports.SupplierRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Refuses a production startup that could only create synthetic reservations. */
@Component
@Profile("!demo & !test")
@ConditionalOnProperty(name = "transfer.aggregator.require-live-supplier", havingValue = "true", matchIfMissing = true)
public class LiveSupplierReadinessGuard {
    private final SupplierRegistry registry;

    public LiveSupplierReadinessGuard(SupplierRegistry registry) { this.registry = registry; }

    @PostConstruct
    void verify() {
        if (registry.getEnabledSuppliers().stream().noneMatch(LiveTransferSupplier.class::isInstance)) {
            throw new IllegalStateException("Production requires a verified live transfer supplier");
        }
    }
}
