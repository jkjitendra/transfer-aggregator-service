package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.config.AggregatorProperties;
import com.arcube.transferaggregator.domain.*;
import com.arcube.transferaggregator.dto.SearchRequest;
import com.arcube.transferaggregator.dto.SearchResponse;
import com.arcube.transferaggregator.dto.SearchResponse.OfferDto;
import com.arcube.transferaggregator.dto.SearchResponse.SupplierStatusDto;
import com.arcube.transferaggregator.ports.SupplierRegistry;
import com.arcube.transferaggregator.ports.SupplierSearchResult;
import com.arcube.transferaggregator.ports.TransferSupplier;
import com.arcube.transferaggregator.resilience.RateLimiter;
import com.arcube.transferaggregator.resilience.SupplierBulkhead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates search across all enabled suppliers.
 * Includes resilience patterns: bulkhead, rate limiting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferSearchService {
    
    private final SupplierRegistry supplierRegistry;
    private final AggregatorProperties properties;
    private final SupplierBulkhead bulkhead;
    private final RateLimiter rateLimiter;
    private final SearchPollingService pollingService;
    
    public SearchResponse search(SearchRequest request) {
        var searchId = UUID.randomUUID().toString();
        var deadline = Instant.now().plusSeconds(properties.getGlobalTimeoutSeconds());
        var command = mapToCommand(request);
        
        List<TransferSupplier> suppliers = supplierRegistry.getEnabledSuppliers();
        log.info("Search {}: {} suppliers enabled, bulkhead permits={}", 
            searchId, suppliers.size(), bulkhead.availablePermits());
        
        if (suppliers.isEmpty()) {
            return SearchResponse.builder()
                .searchId(searchId).offers(List.of()).incomplete(true).supplierStatuses(Map.of())
                .build();
        }
        
        // Fan out to suppliers in parallel with bulkhead protection
        Map<String, CompletableFuture<SupplierSearchResult>> futures = new HashMap<>();
        for (TransferSupplier supplier : suppliers) {
            String code = supplier.getSupplierCode();
            
            Duration timeout = Duration.between(Instant.now(), deadline);
            if (timeout.isNegative()) timeout = Duration.ofMillis(100);
            Duration finalTimeout = timeout;
            
            futures.put(code, CompletableFuture.supplyAsync(() -> {
                // Check rate limit for this supplier
                rateLimiter.acquireSearchPermit(code);
                
                // Execute within bulkhead
                return bulkhead.execute(() -> supplier.search(command, finalTimeout));
            }));
        }
        
        // Gather results
        List<Offer> allOffers = new ArrayList<>();
        Map<String, SupplierStatusDto> statuses = new HashMap<>();
        Map<String, String> supplierSearchIds = new HashMap<>();
        boolean incomplete = false;
        
        for (var entry : futures.entrySet()) {
            String code = entry.getKey();
            try {
                Duration remaining = Duration.between(Instant.now(), deadline);
                SupplierSearchResult result = entry.getValue().get(Math.max(100, remaining.toMillis()), TimeUnit.MILLISECONDS);
                allOffers.addAll(result.offers());
                supplierSearchIds.put(code, result.searchId());
                
                String status = result.complete() ? "SUCCESS" : "POLLING";
                if (result.timedOut()) status = "TIMEOUT";
                
                statuses.put(code, SupplierStatusDto.builder()
                    .status(status)
                    .resultsCount(result.offers().size()).build());
                if (!result.complete() || result.timedOut()) incomplete = true;
                log.info("Supplier {} returned {} offers, complete={}", code, result.offers().size(), result.complete());
            } catch (Exception e) {
                log.error("Supplier {} failed: {}", code, e.getMessage());
                statuses.put(code, SupplierStatusDto.builder().status("ERROR").errorMessage(e.getMessage()).build());
                incomplete = true;
            }
        }
        
        log.info("Search {} complete: {} offers, incomplete={}", searchId, allOffers.size(), incomplete);
        SearchResponse response = SearchResponse.builder()
            .searchId(searchId)
            .offers(allOffers.stream().map(this::mapToOfferDto).toList())
            .incomplete(incomplete)
            .supplierStatuses(statuses)
            .build();
        
        // Cache state for polling if incomplete
        if (incomplete) {
            pollingService.cacheSearchState(searchId, response, supplierSearchIds);
        }
        
        return response;
    }
    
    private SearchCommand mapToCommand(SearchRequest req) {
        return SearchCommand.builder()
            .pickupLocation(mapLocation(req.getPickupLocation()))
            .dropoffLocation(mapLocation(req.getDropoffLocation()))
            .pickupDateTime(req.getPickupDateTime())
            .numPassengers(req.getNumPassengers())
            .numBags(req.getNumBags())
            .currency(req.getCurrency())
            .mode(req.getMode() != null ? SearchCommand.TransferMode.valueOf(req.getMode().name()) : SearchCommand.TransferMode.ONE_WAY)
            .build();
    }
    
    private Location mapLocation(SearchRequest.LocationDto dto) {
        if (dto == null) return null;
        return new Location(dto.getAddress(), dto.getIataCode(), dto.getPlaceId(), 
            dto.getLatitude(), dto.getLongitude(), null, null);
    }
    
    private OfferDto mapToOfferDto(Offer o) {
        return OfferDto.builder()
            .offerId(o.offerId()).supplierCode(o.supplierCode())
            .vehicle(o.vehicle()).provider(o.provider()).totalPrice(o.totalPrice())
            .cancellation(o.cancellation()).estimatedDurationMinutes(o.estimatedDurationMinutes())
            .flightInfoRequired(o.flightInfoRequired()).extraPassengerInfoRequired(o.extraPassengerInfoRequired())
            .expiresAt(o.expiresAt())
            .includedAmenities(o.includedAmenities() != null ? o.includedAmenities().stream().map(Amenity::key).toList() : List.of())
            .extras(o.extras())
            .build();
    }
}

