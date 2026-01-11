package com.arcube.transferaggregator.ports;

import java.util.List;
import java.util.Optional;

/** Registry for transfer suppliers */
public interface SupplierRegistry {
    
    List<TransferSupplier> getEnabledSuppliers();
    
    Optional<TransferSupplier> getSupplier(String supplierCode);
    
    List<TransferSupplier> getAllSuppliers();
}
