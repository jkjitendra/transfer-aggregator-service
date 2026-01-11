package com.arcube.transferaggregator.adapters.supplier;

import com.arcube.transferaggregator.ports.SupplierRegistry;
import com.arcube.transferaggregator.ports.TransferSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds a map of supplierCode → TransferSupplier and provides lookup methods for the service layer */
@Slf4j
@Component
public class SupplierRegistryImpl implements SupplierRegistry {
    
    private final Map<String, TransferSupplier> supplierMap;
    private final List<TransferSupplier> allSuppliers;
    
    public SupplierRegistryImpl(List<TransferSupplier> suppliers) {
        this.allSuppliers = suppliers;
        this.supplierMap = suppliers.stream()
            .collect(Collectors.toMap(TransferSupplier::getSupplierCode, Function.identity(), (a, b) -> a));
        
        log.info("Registered {} suppliers: {}", 
            suppliers.size(), suppliers.stream().map(TransferSupplier::getSupplierCode).toList());
    }
    
    @Override
    public List<TransferSupplier> getEnabledSuppliers() {
        return allSuppliers.stream().filter(TransferSupplier::isEnabled).toList();
    }
    
    @Override
    public Optional<TransferSupplier> getSupplier(String supplierCode) {
        TransferSupplier supplier = supplierMap.get(supplierCode);
        return (supplier != null && supplier.isEnabled()) ? Optional.of(supplier) : Optional.empty();
    }
    
    @Override
    public List<TransferSupplier> getAllSuppliers() {
        return List.copyOf(allSuppliers);
    }
}
