package com.arcube.transferaggregator.adapters.supplier.mozio;

import com.arcube.transferaggregator.adapters.supplier.mozio.client.MozioBookingClient;
import com.arcube.transferaggregator.adapters.supplier.mozio.client.MozioSearchClient;
import com.arcube.transferaggregator.adapters.supplier.mozio.dto.MozioBookingRequest;
import com.arcube.transferaggregator.adapters.supplier.mozio.dto.ReservationStatus;
import com.arcube.transferaggregator.config.SupplierProperties;
import com.arcube.transferaggregator.domain.*;
import com.arcube.transferaggregator.ports.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

// Mozio supplier adapter - connects to Mozio API for real transfers
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "transfer.aggregator.mode", havingValue = "real")
public class MozioSupplierAdapter implements TransferSupplier {
    
    private static final String SUPPLIER_CODE = "MOZIO";
    private static final String SUPPLIER_NAME = "Mozio";
    
    private final MozioSearchClient searchClient;
    private final MozioBookingClient bookingClient;
    private final MozioMapper mapper;
    private final SupplierProperties supplierProperties;
    
    @Override
    public String getSupplierCode() { return SUPPLIER_CODE; }
    
    @Override
    public String getSupplierName() { return SUPPLIER_NAME; }
    
    // Searches Mozio for available transfers, handles polling internally
    @Override
    public SupplierSearchResult search(SearchCommand command, Duration timeout) {
        var request = com.arcube.transferaggregator.adapters.supplier.mozio.dto.MozioSearchRequest.builder()
            .startAddress(command.pickupLocation().address())
            .endAddress(command.dropoffLocation().address())
            .pickupDatetime(command.pickupDateTime().toString())
            .numPassengers(command.numPassengers())
            .currency(command.currency())
            .mode("one_way")
            .build();
            
        var result = searchClient.search(request, timeout);
        
        List<Offer> offers = result.results().stream()
            .map(r -> mapper.mapToOffer(r, result.searchId(), result.expiresAt()))
            .collect(Collectors.toList());
            
        return SupplierSearchResult.success(SUPPLIER_CODE, result.searchId(), offers, result.complete(), 1);
    }
    
    // Creates a booking with Mozio, returns confirmation or pending status
    @Override
    public SupplierBookingResult book(BookCommand command, Duration timeout) {
        var request = MozioBookingRequest.builder()
            .searchId(command.searchId())
            .resultId(command.resultId())
            .email(command.passenger().email())
            .firstName(command.passenger().firstName())
            .lastName(command.passenger().lastName())
            .phoneNumber(command.passenger().phoneNumber())
            .countryCode(command.passenger().countryCode())
            .build();
            
        try {
            var response = bookingClient.book(request);
            
            // Booking failed at provider level
            if (response.getStatus() == ReservationStatus.FAILED) {
                return SupplierBookingResult.failed(SUPPLIER_CODE, "BOOKING_FAILED", "Provider could not confirm booking");
            }
            
            var res = response.getReservations().stream().findFirst().orElseThrow();
            
            // Still processing, need to poll later
            if (response.getStatus() == ReservationStatus.PENDING || res.getStatus() == ReservationStatus.PENDING) {
                return SupplierBookingResult.pending(SUPPLIER_CODE, res.getId());
            }
            
            // Get price and currency from response, fallback to USD if not available
            String currency = res.getCurrency() != null ? res.getCurrency() : "USD";
            double amount = res.getTotalPrice() != null && res.getTotalPrice().getValue() != null 
                ? res.getTotalPrice().getValue().getValue() 
                : 0.0;
            
            return SupplierBookingResult.confirmed(
                SUPPLIER_CODE, res.getId(), res.getConfirmationNumber(), 
                Money.of(amount, currency),
                res.getPickupInstructions());
                
        } catch (MozioApiException e) {
            // Search expired - user needs to search again
            if (e.isSearchExpired()) {
                return SupplierBookingResult.failed(SUPPLIER_CODE, "SEARCH_EXPIRED", "Search has expired. Please search again.");
            }
            // Price changed since search
            if (e.isPriceChanged()) {
                return SupplierBookingResult.priceChanged(SUPPLIER_CODE);
            }
            // Duplicate booking - treat as success (idempotent)
            if (e.isDuplicateReservation()) {
                log.info("Duplicate reservation detected, returning success");
                return SupplierBookingResult.confirmed(SUPPLIER_CODE, "EXISTING", "DUPLICATE", Money.of(0, "USD"), null);
            }
            return SupplierBookingResult.failed(SUPPLIER_CODE, e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Booking failed: {}", e.getMessage());
            return SupplierBookingResult.failed(SUPPLIER_CODE, "BOOKING_FAILED", e.getMessage());
        }
    }
    
    // Cancels a Mozio reservation, handles already-cancelled gracefully
    @Override
    public SupplierCancelResult cancel(CancelCommand command) {
        try {
            bookingClient.cancel(command.reservationId());
            return SupplierCancelResult.success(SUPPLIER_CODE, command.reservationId(), Money.of(0, "USD"));
        } catch (MozioApiException e) {
            // Already cancelled - return success (idempotent)
            if (e.isAlreadyCanceled()) {
                log.info("Reservation {} already cancelled", command.reservationId());
                return SupplierCancelResult.alreadyCancelled(SUPPLIER_CODE, command.reservationId());
            }
            // Too late to cancel
            if (e.isTooLateToCanel()) {
                return SupplierCancelResult.failed(SUPPLIER_CODE, command.reservationId(), 
                    "TOO_LATE_TO_CANCEL", "Cancellation window has passed");
            }
            return SupplierCancelResult.failed(SUPPLIER_CODE, command.reservationId(), e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Cancel failed: {}", e.getMessage());
            return SupplierCancelResult.failed(SUPPLIER_CODE, command.reservationId(), "CANCEL_FAILED", e.getMessage());
        }
    }
    
    @Override
    public boolean isEnabled() { 
        return supplierProperties.getMozio().isEnabled(); 
    }
}
