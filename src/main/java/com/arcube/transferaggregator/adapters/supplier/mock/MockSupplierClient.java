package com.arcube.transferaggregator.adapters.supplier.mock;

import com.arcube.transferaggregator.adapters.idcodec.OfferIdCodec;
import com.arcube.transferaggregator.adapters.idcodec.OfferPayload;
import com.arcube.transferaggregator.config.AggregatorProperties;
import com.arcube.transferaggregator.config.SupplierProperties;
import com.arcube.transferaggregator.domain.*;
import com.arcube.transferaggregator.ports.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "transfer.aggregator.mode", havingValue = "stub", matchIfMissing = true)
public class MockSupplierClient implements TransferSupplier, ReservationChangeSupplier {
    
    private static final String SUPPLIER_CODE = "STUB";
    private static final String SUPPLIER_NAME = "Mock Supplier (Testing)";
    
    private final AggregatorProperties properties;
    private final SupplierProperties supplierProperties;
    private final OfferIdCodec offerIdCodec;
    
    @Override
    public String getSupplierCode() { return SUPPLIER_CODE; }
    
    @Override
    public String getSupplierName() { return SUPPLIER_NAME; }
    
    @Override
    public SupplierSearchResult search(SearchCommand command, Duration timeout) {
        log.info("MOCK: Search from {} to {}", command.pickupLocation(), command.dropoffLocation());
        sleep(500);
        
        String searchId = "mock-" + UUID.randomUUID().toString().substring(0, 8);
        Instant expiresAt = Instant.now().plusSeconds(20 * 60);
        
        List<Offer> offers = List.of(
            createOffer(searchId, "r1", "Standard Sedan", 45.99, expiresAt),
            createOffer(searchId, "r2", "SUV", 65.99, expiresAt),
            createOffer(searchId, "r3", "Executive Sedan", 89.99, expiresAt)
        );
        
        log.info("MOCK: Returning {} offers", offers.size());
        return SupplierSearchResult.success(SUPPLIER_CODE, searchId, offers, true, 1);
    }
    
    @Override
    public SupplierBookingResult book(BookCommand command, Duration timeout) {
        log.info("MOCK: Booking for searchId={}, resultId={}", command.searchId(), command.resultId());
        sleep(300);
        
        String reservationId = "res-" + UUID.randomUUID().toString().substring(0, 8);
        String confirmationNumber = "MOCK" + System.currentTimeMillis();
        
        log.info("MOCK: Confirmed with {}", confirmationNumber);
        return SupplierBookingResult.confirmed(
            SUPPLIER_CODE, reservationId, confirmationNumber,
            Money.of(45.99, "USD"),
            "Driver will meet you at arrivals exit with a sign.");
    }
    
    @Override
    public SupplierCancelResult cancel(CancelCommand command) {
        log.info("MOCK: Cancelling {}", command.reservationId());
        sleep(200);
        log.info("MOCK: Cancelled");
        return SupplierCancelResult.success(SUPPLIER_CODE, command.reservationId(), Money.of(45.99, "USD"));
    }
    
    @Override
    public boolean isEnabled() { 
        return supplierProperties.getStub().isEnabled(); 
    }

    // ========== ReservationChangeSupplier Implementation ==========
    
    @Override
    public boolean supportsReservationChanges() {
        return true; // Mock supports reservation changes
    }

    @Override
    public SupplierReservationChangeSearchResult searchForChange(
            ReservationChangeSearchCommand command, Duration timeout) {
        log.info("MOCK: Searching for change options, oldReservation={}", command.reservationId());
        sleep(400);
        
        String searchId = "change-" + UUID.randomUUID().toString().substring(0, 8);
        Instant expiresAt = Instant.now().plusSeconds(20 * 60);
        
        // Return offers from same provider (simulating Mozio behavior)
        List<Offer> offers = List.of(
            createOffer(searchId, "cr1", "Standard Sedan (Changed)", 49.99, expiresAt),
            createOffer(searchId, "cr2", "SUV (Changed)", 69.99, expiresAt)
        );
        
        log.info("MOCK: Found {} change options", offers.size());
        return SupplierReservationChangeSearchResult.success(
            searchId, offers, command.reservationId(), SUPPLIER_CODE);
    }

    @Override
    public SupplierReservationChangeResult changeReservation(
            ReservationChangeCommitCommand command, Duration timeout) {
        log.info("MOCK: Changing reservation, old={}, new search={}, result={}", 
            command.oldReservationId(), command.searchId(), command.resultId());
        sleep(500);
        
        String newReservationId = "res-new-" + UUID.randomUUID().toString().substring(0, 8);
        String newConfirmation = "MOCKNEW" + System.currentTimeMillis();
        
        log.info("MOCK: Changed! New reservation={}, confirmation={}", newReservationId, newConfirmation);
        
        Money newPrice = Money.of(49.99, "USD");
        Money priceDiff = Money.of(4.00, "USD"); // Customer owes $4 more
        
        return SupplierReservationChangeResult.success(
            newReservationId, newConfirmation, command.oldReservationId(), newPrice, priceDiff);
    }
    
    // ========== Helper Methods ==========
    
    private Offer createOffer(String searchId, String resultId, String vehicleType, double price, Instant expiresAt) {
        OfferPayload payload = OfferPayload.of(SUPPLIER_CODE, searchId, resultId, expiresAt);
        String offerId = offerIdCodec.encode(payload);
        
        // Simulate varying distances (Mozio provides distance_meters)
        int distanceMeters = switch (vehicleType) {
            case "Standard Sedan" -> 15200;       // ~15km
            case "SUV" -> 18500;                  // ~18km
            case "Executive Sedan" -> 12800;      // ~13km (closer luxury)
            default -> 16000;
        };
        
        return Offer.builder()
            .offerId(offerId)
            .supplierCode(SUPPLIER_CODE)
            .vehicle(Vehicle.builder()
                .type(vehicleType)
                .category("Private")
                .vehicleClass(vehicleType.contains("Executive") ? "Business" : "Standard")
                .maxPassengers(vehicleType.contains("SUV") ? 6 : 3)
                .maxBags(vehicleType.contains("SUV") ? 6 : 3)
                .build())
            .provider(Provider.builder()
                .name("Mock Transfers")
                .displayName("Mock Transfers")
                .rating(BigDecimal.valueOf(4.8))
                .ratingCount(1200)
                .build())
            .totalPrice(Money.of(price, "USD"))
            .cancellation(CancellationPolicy.builder()
                .cancellableOnline(true)
                .cancellableOffline(true)
                .tiers(List.of(
                    new CancellationPolicy.CancellationTier(24, 100),
                    new CancellationPolicy.CancellationTier(12, 50),
                    new CancellationPolicy.CancellationTier(0, 0)
                ))
                .build())
            .estimatedDurationMinutes(35)
            .distanceMeters(distanceMeters)
            .flightInfoRequired(true)
            .extraPassengerInfoRequired(false)
            .expiresAt(expiresAt)
            .includedAmenities(List.of(Amenity.included("ride_tracking", "Ride Tracking")))
            .build();
    }
    
    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
