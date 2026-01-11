package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.adapters.supplier.mock.SlowMockSupplierClient;
import com.arcube.transferaggregator.domain.Amenity;
import com.arcube.transferaggregator.domain.Offer;
import com.arcube.transferaggregator.dto.SearchResponse;
import com.arcube.transferaggregator.dto.SearchResponse.OfferDto;
import com.arcube.transferaggregator.dto.SearchResponse.SupplierStatusDto;
import com.arcube.transferaggregator.ports.SupplierSearchResult;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** Handles polling for search results from slow suppliers */
@Slf4j
@Service
public class SearchPollingService {
    
    private final SlowMockSupplierClient slowMockClient;
    
    // Cache: aggregator searchId -> SearchState
    private final Cache<String, SearchState> searchCache = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .maximumSize(5000)
        .build();
    
    public SearchPollingService(Optional<SlowMockSupplierClient> slowMockClient) {
        this.slowMockClient = slowMockClient.orElse(null);
    }
    
    /** Cache initial search state for polling */
    public void cacheSearchState(String searchId, SearchResponse response, 
                                  Map<String, String> supplierSearchIds) {
        SearchState state = new SearchState(
            searchId, 
            new ArrayList<>(response.getOffers()),
            new HashMap<>(response.getSupplierStatuses()),
            new HashMap<>(supplierSearchIds),
            response.isIncomplete()
        );
        searchCache.put(searchId, state);
        log.debug("Cached search: id={}, incomplete={}, suppliers={}", 
            searchId, response.isIncomplete(), supplierSearchIds.keySet());
    }
    
    /** Poll for updated results */
    public SearchResponse poll(String searchId) {
        SearchState state = searchCache.getIfPresent(searchId);
        if (state == null) {
            log.warn("Poll for unknown searchId: {}", searchId);
            return SearchResponse.builder()
                .searchId(searchId)
                .offers(List.of())
                .incomplete(false)
                .supplierStatuses(Map.of())
                .build();
        }
        
        // Synchronize on state to prevent race condition on concurrent polls
        synchronized (state) {
            if (!state.incomplete) {
                return buildResponse(searchId, state);
            }
            
            // Poll slow supplier
            boolean stillIncomplete = false;
            if (slowMockClient != null) {
                String slowSearchId = state.supplierSearchIds.get("SLOW_STUB");
                if (slowSearchId != null && slowMockClient.hasSearch(slowSearchId)) {
                    SupplierSearchResult result = slowMockClient.poll(slowSearchId);
                    
                    // Update offers from SLOW_STUB
                    state.offers.removeIf(o -> "SLOW_STUB".equals(o.getSupplierCode()));
                    state.offers.addAll(result.offers().stream().map(this::mapToDto).toList());
                    
                    state.statuses.put("SLOW_STUB", SupplierStatusDto.builder()
                        .status(result.complete() ? "SUCCESS" : "POLLING")
                        .resultsCount(result.offers().size())
                        .build());
                    
                    stillIncomplete = !result.complete();
                }
            }
            
            state.incomplete = stillIncomplete;
            log.info("Poll {}: {} offers, incomplete={}", searchId, state.offers.size(), stillIncomplete);
            
            return buildResponse(searchId, state);
        }
    }
    
    private SearchResponse buildResponse(String searchId, SearchState state) {
        return SearchResponse.builder()
            .searchId(searchId)
            .offers(new ArrayList<>(state.offers))
            .incomplete(state.incomplete)
            .supplierStatuses(new HashMap<>(state.statuses))
            .build();
    }
    
    private OfferDto mapToDto(Offer o) {
        return OfferDto.builder()
            .offerId(o.offerId()).supplierCode(o.supplierCode())
            .vehicle(o.vehicle()).provider(o.provider()).totalPrice(o.totalPrice())
            .cancellation(o.cancellation()).estimatedDurationMinutes(o.estimatedDurationMinutes())
            .flightInfoRequired(o.flightInfoRequired()).extraPassengerInfoRequired(o.extraPassengerInfoRequired())
            .expiresAt(o.expiresAt())
            .includedAmenities(o.includedAmenities() != null ? 
                o.includedAmenities().stream().map(Amenity::key).toList() : List.of())
            .extras(o.extras())
            .build();
    }
    
    private static class SearchState {
        final String searchId;
        final List<OfferDto> offers;
        final Map<String, SupplierStatusDto> statuses;
        final Map<String, String> supplierSearchIds;
        boolean incomplete;
        
        SearchState(String searchId, List<OfferDto> offers, 
                   Map<String, SupplierStatusDto> statuses,
                   Map<String, String> supplierSearchIds, boolean incomplete) {
            this.searchId = searchId;
            this.offers = offers;
            this.statuses = statuses;
            this.supplierSearchIds = supplierSearchIds;
            this.incomplete = incomplete;
        }
    }
}
