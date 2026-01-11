package com.arcube.transferaggregator.adapters.supplier.mock;

import com.arcube.transferaggregator.adapters.idcodec.OfferIdCodec;
import com.arcube.transferaggregator.adapters.idcodec.OfferPayload;
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
import java.util.Map;
import java.util.UUID;

/**
 * SkyRide mock supplier for testing multi-supplier aggregation.
 * Returns electric/premium vehicle offers with different pricing than STUB.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "transfer.aggregator.suppliers.skyride.enabled", havingValue = "true", matchIfMissing = false)
public class SkyRideSupplierClient implements TransferSupplier {
    
    private static final String SUPPLIER_CODE = "SKYRIDE";
    private static final String SUPPLIER_NAME = "SkyRide Premium Transfers";
    
    private final SupplierProperties supplierProperties;
    private final OfferIdCodec offerIdCodec;
    
    @Override
    public String getSupplierCode() { return SUPPLIER_CODE; }
    
    @Override
    public String getSupplierName() { return SUPPLIER_NAME; }
    
    @Override
    public SupplierSearchResult search(SearchCommand command, Duration timeout) {
        log.info("SKYRIDE: Search from {} to {}", command.pickupLocation(), command.dropoffLocation());
        
        // Simulate network latency
        sleep(400);
        
        String searchId = "skyride-" + UUID.randomUUID().toString().substring(0, 8);
        Instant expiresAt = Instant.now().plusSeconds(15 * 60); // 15 min validity
        
        List<Offer> offers = List.of(
            createOffer(searchId, "sr1", "Electric Sedan", 55.99, expiresAt,
                Map.of(
                    "greenVehicle", true,
                    "carbonOffset", "100% offset",
                    "chargingType", "Tesla Supercharger"
                )),
            createOffer(searchId, "sr2", "Premium Van", 95.99, expiresAt,
                Map.of(
                    "wifiOnboard", true,
                    "refreshments", List.of("water", "snacks"),
                    "entertainmentSystem", "10-inch screens"
                )),
            createOffer(searchId, "sr3", "Luxury Tesla", 150.99, expiresAt,
                Map.of(
                    "greenVehicle", true,
                    "autopilot", true,
                    "premiumSound", "Bose Premium Audio",
                    "climateZones", 3
                ))
        );
        
        log.info("SKYRIDE: Returning {} offers", offers.size());
        return SupplierSearchResult.success(SUPPLIER_CODE, searchId, offers, true, 1);
    }
    
    @Override
    public SupplierBookingResult book(BookCommand command, Duration timeout) {
        log.info("SKYRIDE: Booking for searchId={}, resultId={}", command.searchId(), command.resultId());
        
        sleep(350);
        
        String reservationId = "sky-" + UUID.randomUUID().toString().substring(0, 8);
        String confirmationNumber = "SKY" + System.currentTimeMillis();
        
        log.info("SKYRIDE: Confirmed with {}", confirmationNumber);
        return SupplierBookingResult.confirmed(
            SUPPLIER_CODE, reservationId, confirmationNumber,
            Money.of(55.99, "USD"),
            "Your SkyRide driver will meet you at the designated pickup zone with a tablet showing your name.");
    }
    
    @Override
    public SupplierCancelResult cancel(CancelCommand command) {
        log.info("SKYRIDE: Cancelling {}", command.reservationId());
        sleep(200);
        log.info("SKYRIDE: Cancelled");
        return SupplierCancelResult.success(SUPPLIER_CODE, command.reservationId(), Money.of(55.99, "USD"));
    }
    
    @Override
    public boolean isEnabled() { 
        return supplierProperties.getSkyride().isEnabled(); 
    }
    
    private Offer createOffer(String searchId, String resultId, String vehicleType, 
                               double price, Instant expiresAt, Map<String, Object> extras) {
        OfferPayload payload = OfferPayload.of(SUPPLIER_CODE, searchId, resultId, expiresAt);
        String offerId = offerIdCodec.encode(payload);
        
        return Offer.builder()
            .offerId(offerId)
            .supplierCode(SUPPLIER_CODE)
            .vehicle(Vehicle.builder()
                .type(vehicleType)
                .category("Premium")
                .vehicleClass(vehicleType.contains("Luxury") ? "Luxury" : "Premium")
                .maxPassengers(vehicleType.contains("Van") ? 8 : 4)
                .maxBags(vehicleType.contains("Van") ? 8 : 4)
                .build())
            .provider(Provider.builder()
                .name("SkyRide")
                .displayName("SkyRide Premium Transfers")
                .rating(BigDecimal.valueOf(4.9))
                .ratingCount(2500)
                .build())
            .totalPrice(Money.of(price, "USD"))
            .cancellation(CancellationPolicy.builder()
                .cancellableOnline(true)
                .cancellableOffline(true)
                .tiers(List.of(
                    new CancellationPolicy.CancellationTier(48, 100),
                    new CancellationPolicy.CancellationTier(24, 75),
                    new CancellationPolicy.CancellationTier(0, 0)
                ))
                .build())
            .estimatedDurationMinutes(30)
            .flightInfoRequired(true)
            .extraPassengerInfoRequired(false)
            .expiresAt(expiresAt)
            .includedAmenities(List.of(
                Amenity.included("wifi", "Free WiFi"),
                Amenity.included("ride_tracking", "Live Tracking"),
                Amenity.included("meet_greet", "Meet & Greet Service")
            ))
            .extras(extras)
            .build();
    }
    
    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
