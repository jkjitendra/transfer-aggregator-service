package com.arcube.transferaggregator.resilience;

import com.arcube.transferaggregator.config.AggregatorProperties;

import java.time.Duration;

/** Per-supplier operation budgets, capped by the caller's remaining deadline. */
public final class SupplierOperationPolicy {
    public enum Operation { SEARCH, BOOKING, CHANGE, CANCELLATION }

    private SupplierOperationPolicy() {}

    public static Duration timeout(AggregatorProperties properties, String supplier,
                                   Operation operation, Duration remaining) {
        int seconds = switch (operation) {
            case SEARCH, BOOKING, CHANGE -> 10;
            case CANCELLATION -> 5;
        };
        if (properties != null && properties.getResilience() != null
                && properties.getResilience().getSupplierTimeouts() != null) {
            var configured = properties.getResilience().getSupplierTimeouts().get(supplier);
            if (configured != null) seconds = switch (operation) {
                case SEARCH -> configured.getSearchSeconds();
                case BOOKING -> configured.getBookingSeconds();
                case CHANGE -> configured.getChangeSeconds();
                case CANCELLATION -> configured.getCancellationSeconds();
            };
        }
        if (seconds < 1 || seconds > 120) throw new IllegalStateException("Supplier timeout must be 1-120 seconds");
        Duration budget = Duration.ofSeconds(seconds);
        if (remaining != null && remaining.compareTo(budget) < 0) budget = remaining;
        return budget.isNegative() || budget.isZero() ? Duration.ofMillis(100) : budget;
    }
}
