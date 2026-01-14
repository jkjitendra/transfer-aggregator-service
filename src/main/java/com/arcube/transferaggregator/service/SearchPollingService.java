package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.adapters.supplier.mock.SlowMockSupplierClient;
import com.arcube.transferaggregator.domain.Amenity;
import com.arcube.transferaggregator.domain.Offer;
import com.arcube.transferaggregator.dto.PageRequest;
import com.arcube.transferaggregator.dto.SearchFilter;
import com.arcube.transferaggregator.dto.SearchResponse;
import com.arcube.transferaggregator.dto.SearchResponse.OfferDto;
import com.arcube.transferaggregator.dto.SearchResponse.SupplierStatusDto;
import com.arcube.transferaggregator.dto.SearchSort;
import com.arcube.transferaggregator.dto.SearchStateDto;
import com.arcube.transferaggregator.ports.SupplierSearchResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
public class SearchPollingService {
    
    private static final String CACHE_PREFIX = "search:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    
    private final SlowMockSupplierClient slowMockClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final OfferFilterService filterService;
    
    public SearchPollingService(Optional<SlowMockSupplierClient> slowMockClient,
                                 StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 OfferFilterService filterService) {
        this.slowMockClient = slowMockClient.orElse(null);
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.filterService = filterService;
    }
    
    public void cacheSearchState(String searchId, SearchResponse response, 
                                  Map<String, String> supplierSearchIds) {
        SearchStateDto state = SearchStateDto.builder()
            .searchId(searchId)
            .offers(new ArrayList<>(response.getOffers()))
            .statuses(new HashMap<>(response.getSupplierStatuses()))
            .supplierSearchIds(new HashMap<>(supplierSearchIds))
            .incomplete(response.isIncomplete())
            .build();
        
        try {
            String cacheKey = CACHE_PREFIX + searchId;
            String json = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
            log.debug("Cached search in Redis: id={}, incomplete={}, suppliers={}", 
                searchId, response.isIncomplete(), supplierSearchIds.keySet());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize search state for caching: {}", e.getMessage());
        }
    }
    
    /**
     * Poll with filtering, sorting, and pagination.
     */
    public SearchResponse poll(String searchId, SearchFilter filter, 
                                SearchSort sort, PageRequest page) {
        String cacheKey = CACHE_PREFIX + searchId;
        String json = redisTemplate.opsForValue().get(cacheKey);
        
        if (json == null) {
            log.warn("Poll for unknown searchId: {}", searchId);
            return SearchResponse.builder()
                .searchId(searchId)
                .offers(List.of())
                .incomplete(false)
                .supplierStatuses(Map.of())
                .totalCount(0)
                .page(0)
                .totalPages(0)
                .build();
        }
        
        SearchStateDto state;
        try {
            state = objectMapper.readValue(json, SearchStateDto.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize search state: {}", e.getMessage());
            return SearchResponse.builder()
                .searchId(searchId)
                .offers(List.of())
                .incomplete(false)
                .supplierStatuses(Map.of())
                .build();
        }
        
        // Poll slow supplier if still incomplete
        boolean stillIncomplete = false;
        if (state.isIncomplete() && slowMockClient != null) {
            String slowSearchId = state.getSupplierSearchIds().get("SLOW_STUB");
            if (slowSearchId != null && slowMockClient.hasSearch(slowSearchId)) {
                SupplierSearchResult result = slowMockClient.poll(slowSearchId);
                
                state.getOffers().removeIf(o -> "SLOW_STUB".equals(o.getSupplierCode()));
                state.getOffers().addAll(result.offers().stream().map(this::mapToDto).toList());
                
                state.getStatuses().put("SLOW_STUB", SupplierStatusDto.builder()
                    .status(result.complete() ? "SUCCESS" : "POLLING")
                    .resultsCount(result.offers().size())
                    .build());
                
                stillIncomplete = !result.complete();
            }
        }
        
        state.setIncomplete(stillIncomplete);
        
        // Update cache
        try {
            String updatedJson = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(cacheKey, updatedJson, CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to update cache: {}", e.getMessage());
        }
        
        // Apply filtering, sorting, pagination
        OfferFilterService.FilterResult filterResult = 
            filterService.filterAndSort(state.getOffers(), filter, sort, page);
        
        log.info("Poll {}: {} total offers, {} after filter, page {}/{}", 
            searchId, state.getOffers().size(), filterResult.getOffers().size(), 
            page.getPage() + 1, filterResult.getTotalPages());
        
        return SearchResponse.builder()
            .searchId(searchId)
            .offers(filterResult.getOffers())
            .incomplete(stillIncomplete)
            .supplierStatuses(new HashMap<>(state.getStatuses()))
            .totalCount(filterResult.getTotalCount())
            .page(filterResult.getPage())
            .totalPages(filterResult.getTotalPages())
            .hasNext(filterResult.isHasNext())
            .hasPrevious(filterResult.isHasPrevious())
            .build();
    }
    
    /**
     * Backward compatible poll without filters.
     */
    public SearchResponse poll(String searchId) {
        return poll(searchId, null, SearchSort.byPrice(), PageRequest.first());
    }
    
    private OfferDto mapToDto(Offer o) {
        return OfferDto.builder()
            .offerId(o.offerId()).supplierCode(o.supplierCode())
            .vehicle(o.vehicle()).provider(o.provider()).totalPrice(o.totalPrice())
            .cancellation(o.cancellation()).estimatedDurationMinutes(o.estimatedDurationMinutes())
            .distanceMeters(o.distanceMeters())
            .flightInfoRequired(o.flightInfoRequired()).extraPassengerInfoRequired(o.extraPassengerInfoRequired())
            .expiresAt(o.expiresAt())
            .includedAmenities(o.includedAmenities() != null ? 
                o.includedAmenities().stream().map(Amenity::key).toList() : List.of())
            .extras(o.extras())
            .build();
    }
}
