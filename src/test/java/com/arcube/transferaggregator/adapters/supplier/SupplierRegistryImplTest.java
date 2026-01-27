package com.arcube.transferaggregator.adapters.supplier;

import com.arcube.transferaggregator.ports.TransferSupplier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierRegistryImplTest {

    private static final class TestSupplier implements TransferSupplier {
        private final String code;
        private final boolean enabled;

        private TestSupplier(String code, boolean enabled) {
            this.code = code;
            this.enabled = enabled;
        }

        @Override
        public String getSupplierCode() {
            return code;
        }

        @Override
        public String getSupplierName() {
            return code + "-name";
        }

        @Override
        public com.arcube.transferaggregator.ports.SupplierSearchResult search(
            com.arcube.transferaggregator.domain.SearchCommand command, Duration timeout) {
            throw new UnsupportedOperationException("not needed for registry tests");
        }

        @Override
        public com.arcube.transferaggregator.ports.SupplierBookingResult book(
            com.arcube.transferaggregator.domain.BookCommand command, Duration timeout) {
            throw new UnsupportedOperationException("not needed for registry tests");
        }

        @Override
        public com.arcube.transferaggregator.ports.SupplierCancelResult cancel(
            com.arcube.transferaggregator.domain.CancelCommand command) {
            throw new UnsupportedOperationException("not needed for registry tests");
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }

    @Test
    void returnsOnlyEnabledSuppliers() {
        TransferSupplier enabled = new TestSupplier("A", true);
        TransferSupplier disabled = new TestSupplier("B", false);
        SupplierRegistryImpl registry = new SupplierRegistryImpl(List.of(enabled, disabled));

        assertThat(registry.getEnabledSuppliers()).containsExactly(enabled);
        assertThat(registry.getSupplier("B")).isEmpty();
        assertThat(registry.getSupplier("A")).contains(enabled);
    }

    @Test
    void resolvesDuplicateCodesToFirstSeen() {
        TransferSupplier first = new TestSupplier("DUP", true);
        TransferSupplier second = new TestSupplier("DUP", true);
        SupplierRegistryImpl registry = new SupplierRegistryImpl(List.of(first, second));

        Optional<TransferSupplier> resolved = registry.getSupplier("DUP");
        assertThat(resolved).contains(first);
    }

    @Test
    void returnsEmptyWhenSupplierMissingAndExposesAllSuppliers() {
        TransferSupplier first = new TestSupplier("A", true);
        TransferSupplier second = new TestSupplier("B", false);
        SupplierRegistryImpl registry = new SupplierRegistryImpl(List.of(first, second));

        assertThat(registry.getSupplier("MISSING")).isEmpty();
        assertThat(registry.getAllSuppliers()).containsExactly(first, second);
    }
}
