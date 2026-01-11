package com.arcube.transferaggregator.ports;

import com.arcube.transferaggregator.domain.BookCommand;
import com.arcube.transferaggregator.domain.CancelCommand;
import com.arcube.transferaggregator.domain.SearchCommand;

import java.time.Duration;

/**
 * Port interface for transfer suppliers.
 * Each supplier (Mozio, future suppliers) implements this interface.
 */
public interface TransferSupplier {
    
    String getSupplierCode();
    
    String getSupplierName();
    
    SupplierSearchResult search(SearchCommand command, Duration timeout);
    
    SupplierBookingResult book(BookCommand command, Duration timeout);
    
    SupplierCancelResult cancel(CancelCommand command);
    
    boolean isEnabled();
}
