package com.arcube.transferaggregator.adapters.supplier.mock;

import com.arcube.transferaggregator.adapters.idcodec.OfferIdCodec;
import com.arcube.transferaggregator.adapters.idcodec.OfferPayload;
import com.arcube.transferaggregator.config.AggregatorProperties;
import com.arcube.transferaggregator.config.SupplierProperties;
import com.arcube.transferaggregator.domain.*;
import com.arcube.transferaggregator.ports.*;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


// Slow mock supplier for demonstrating polling behavior.
// Returns results progressively over 3 calls (initial + 2 polls).
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "transfer.aggregator.suppliers.slow-stub.enabled", havingValue = "true")
public class SlowMockSupplierClient implements TransferSupplier {
    
    private static final String SUPPLIER_CODE = "SLOW_STUB";
    private static final String SUPPLIER_NAME = "Slow Mock (Polling Demo)";
    private static final int MAX_POLLS = 4;
    
    private final AggregatorProperties properties;
    private final SupplierProperties supplierProperties;
    private final OfferIdCodec offerIdCodec;
    
    // Track poll count per searchId (expires after 5 minutes)
    private final Cache<String, SearchState> searchStates = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .maximumSize(1000)
        .build();
    
    @Override
    public String getSupplierCode() { return SUPPLIER_CODE; }
    
    @Override
    public String getSupplierName() { return SUPPLIER_NAME; }
    
    @Override
    public SupplierSearchResult search(SearchCommand command, Duration timeout) {
        String searchId = "slow-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("SLOW_STUB: Initial search, searchId={}", searchId);
        
        // Simulate initial delay
        sleep(800);
        
        // Initialize state for this search
        SearchState state = new SearchState(searchId, Instant.now().plusSeconds(20 * 60));
        searchStates.put(searchId, state);
        
        // Return 1 offer, incomplete (Mozio-style to start)
        List<Offer> offers = List.of(
            createMozioStyleOffer(searchId, "mozio-sedan", "Toyota Camry Sedan", 89.99, state.expiresAt,
                Map.of(
                    "providerName", "Carzen+",
                    "vehicleMake", "Toyota",
                    "vehicleModel", "Camry",
                    "rideTracking", true,
                    "meetGreet", true
                ))
        );
        
        int pollCount = state.pollCount.incrementAndGet();
        boolean complete = pollCount >= MAX_POLLS;
        
        log.info("SLOW_STUB: Returning {} offers, poll {}/{}, complete={}", 
            offers.size(), pollCount, MAX_POLLS, complete);
        
        return SupplierSearchResult.success(SUPPLIER_CODE, searchId, offers, complete, pollCount);
    }
    
    // Called during polling to get additional results.
    // Each poll returns one new offer alternating between Mozio-style and SkyRide-style vendors.
    public SupplierSearchResult poll(String searchId) {
        SearchState state = searchStates.getIfPresent(searchId);
        if (state == null) {
            log.warn("SLOW_STUB: Unknown searchId for poll: {}", searchId);
            return SupplierSearchResult.success(SUPPLIER_CODE, searchId, List.of(), true, 0);
        }
        
        // Simulate processing delay
        sleep(500);
        
        int pollCount = state.pollCount.incrementAndGet();
        boolean complete = pollCount >= MAX_POLLS;
        
        // Progressively add offers - alternating between Mozio-style and SkyRide-style
        List<Offer> offers = new ArrayList<>();
        
        // Poll 1: Mozio-style Sedan
        offers.add(createMozioStyleOffer(searchId, "mozio-sedan", "Toyota Camry Sedan", 89.99, state.expiresAt,
            Map.of(
                "providerName", "Carzen+",
                "vehicleMake", "Toyota",
                "vehicleModel", "Camry",
                "rideTracking", true,
                "meetGreet", true
            )));
        
        // Poll 2: Add SkyRide-style Electric
        if (pollCount >= 2) {
            offers.add(createSkyRideStyleOffer(searchId, "skyride-electric", "Tesla Model 3", 120.99, state.expiresAt,
                Map.of(
                    "greenVehicle", true,
                    "carbonOffset", "100% offset",
                    "autopilot", true,
                    "premiumSound", "Tesla Premium Audio"
                )));
        }
        
        // Poll 3: Add Mozio-style SUV
        if (pollCount >= 3) {
            offers.add(createMozioStyleOffer(searchId, "mozio-suv", "Ford Explorer SUV", 149.99, state.expiresAt,
                Map.of(
                    "providerName", "Premium Rides",
                    "vehicleMake", "Ford",
                    "vehicleModel", "Explorer",
                    "maxPassengers", 6,
                    "meetGreet", true
                )));
        }
        
        // Poll 4: Add SkyRide-style Van
        if (pollCount >= 4) {
            offers.add(createSkyRideStyleOffer(searchId, "skyride-van", "Mercedes Sprinter Van", 199.99, state.expiresAt,
                Map.of(
                    "wifiOnboard", true,
                    "refreshments", List.of("water", "snacks", "coffee"),
                    "entertainmentSystem", "12-inch screens",
                    "climateZones", 3
                )));
        }
        
        log.info("SLOW_STUB: Poll {}/{} for {}, returning {} offers, complete={}", 
            pollCount, MAX_POLLS, searchId, offers.size(), complete);
        
        return SupplierSearchResult.success(SUPPLIER_CODE, searchId, offers, complete, pollCount);
    }
    
    // Check if we have state for a given searchId (for polling support).
    public boolean hasSearch(String searchId) {
        return searchStates.getIfPresent(searchId) != null;
    }
    
    @Override
    public SupplierBookingResult book(BookCommand command, Duration timeout) {
        log.info("SLOW_STUB: Booking for searchId={}", command.searchId());
        sleep(400);
        
        String reservationId = "slow-res-" + UUID.randomUUID().toString().substring(0, 8);
        String confirmationNumber = "SLOW" + System.currentTimeMillis();
        
        return SupplierBookingResult.confirmed(
            SUPPLIER_CODE, reservationId, confirmationNumber,
            Money.of(54.99, "USD"),
            "Your driver will contact you 30 minutes before pickup.");
    }
    
    @Override
    public SupplierCancelResult cancel(CancelCommand command) {
        log.info("SLOW_STUB: Cancelling {}", command.reservationId());
        sleep(300);
        return SupplierCancelResult.success(SUPPLIER_CODE, command.reservationId(), Money.of(54.99, "USD"));
    }
    
    @Override
    public boolean isEnabled() { 
        return supplierProperties.getSlowStub().isEnabled(); 
    }
    
    // Creates a Mozio-style offer with provider details in extras
    private Offer createMozioStyleOffer(String searchId, String resultId, String vehicleType, 
                                         double price, Instant expiresAt, Map<String, Object> extras) {
        OfferPayload payload = OfferPayload.of(SUPPLIER_CODE, searchId, resultId, expiresAt);
        String offerId = offerIdCodec.encode(payload);
        
        return Offer.builder()
            .offerId(offerId)
            .supplierCode(SUPPLIER_CODE)
            .vehicle(Vehicle.builder()
                .type(vehicleType)
                .category("Private")
                .vehicleClass(vehicleType.contains("SUV") ? "Business" : "Standard")
                .maxPassengers(vehicleType.contains("SUV") ? 6 : 3)
                .maxBags(vehicleType.contains("SUV") ? 5 : 3)
                .build())
            .provider(Provider.builder()
                .name((String) extras.getOrDefault("providerName", "Mozio Partner"))
                .displayName((String) extras.getOrDefault("providerName", "Mozio Partner"))
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
            .flightInfoRequired(true)
            .extraPassengerInfoRequired(false)
            .expiresAt(expiresAt)
            .includedAmenities(List.of(
                Amenity.included("ride_tracking", "Ride Tracking"),
                Amenity.included("meet_greet", "Meet & Greet")
            ))
            .extras(extras)
            .build();
    }
    
    // Creates a SkyRide-style offer with eco/premium extras
    private Offer createSkyRideStyleOffer(String searchId, String resultId, String vehicleType, 
                                           double price, Instant expiresAt, Map<String, Object> extras) {
        OfferPayload payload = OfferPayload.of(SUPPLIER_CODE, searchId, resultId, expiresAt);
        String offerId = offerIdCodec.encode(payload);
        
        return Offer.builder()
            .offerId(offerId)
            .supplierCode(SUPPLIER_CODE)
            .vehicle(Vehicle.builder()
                .type(vehicleType)
                .category("Premium")
                .vehicleClass(vehicleType.contains("Van") ? "Premium" : "Luxury")
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
    
    // Internal state for tracking poll count per search.
    private record SearchState(String searchId, Instant expiresAt, AtomicInteger pollCount) {
        SearchState(String searchId, Instant expiresAt) {
            this(searchId, expiresAt, new AtomicInteger(0));
        }
    }
}
