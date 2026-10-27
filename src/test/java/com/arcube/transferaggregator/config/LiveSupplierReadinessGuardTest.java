package com.arcube.transferaggregator.config;

import com.arcube.transferaggregator.ports.LiveTransferSupplier;
import com.arcube.transferaggregator.ports.SupplierRegistry;
import com.arcube.transferaggregator.ports.TransferSupplier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveSupplierReadinessGuardTest {
    @Test
    void rejectsOnlySyntheticSuppliers() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        when(registry.getEnabledSuppliers()).thenReturn(List.of(mock(TransferSupplier.class)));
        assertThatThrownBy(() -> new LiveSupplierReadinessGuard(registry).verify())
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("live transfer supplier");
        when(registry.getEnabledSuppliers()).thenReturn(List.of(mock(LiveTransferSupplier.class)));
        new LiveSupplierReadinessGuard(registry).verify();
    }
}
