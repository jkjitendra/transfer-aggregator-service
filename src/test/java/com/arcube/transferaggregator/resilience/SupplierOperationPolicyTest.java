package com.arcube.transferaggregator.resilience;

import com.arcube.transferaggregator.config.AggregatorProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierOperationPolicyTest {
    @Test
    void supplierBudgetsAreOperationSpecificAndCappedByRemainingDeadline() {
        AggregatorProperties properties = new AggregatorProperties();
        var supplier = new AggregatorProperties.OperationTimeoutProperties();
        supplier.setSearchSeconds(3);
        supplier.setBookingSeconds(7);
        properties.getResilience().setSupplierTimeouts(Map.of("STUB", supplier));
        assertThat(SupplierOperationPolicy.timeout(properties, "STUB",
            SupplierOperationPolicy.Operation.SEARCH, Duration.ofSeconds(8))).isEqualTo(Duration.ofSeconds(3));
        assertThat(SupplierOperationPolicy.timeout(properties, "STUB",
            SupplierOperationPolicy.Operation.BOOKING, Duration.ofSeconds(2))).isEqualTo(Duration.ofSeconds(2));
        assertThat(SupplierOperationPolicy.timeout(properties, "STUB",
            SupplierOperationPolicy.Operation.CANCELLATION, null)).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void invalidTimeoutFailsClosed() {
        AggregatorProperties properties = new AggregatorProperties();
        var supplier = new AggregatorProperties.OperationTimeoutProperties();
        supplier.setBookingSeconds(0);
        properties.getResilience().setSupplierTimeouts(Map.of("STUB", supplier));
        assertThatThrownBy(() -> SupplierOperationPolicy.timeout(properties, "STUB",
            SupplierOperationPolicy.Operation.BOOKING, null)).isInstanceOf(IllegalStateException.class);
    }
}
