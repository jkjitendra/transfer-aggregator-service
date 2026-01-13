package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.adapters.supplier.mock.SlowMockSupplierClient;
import com.arcube.transferaggregator.domain.Amenity;
import com.arcube.transferaggregator.domain.Offer;
import com.arcube.transferaggregator.dto.SearchResponse;
import com.arcube.transferaggregator.dto.SearchResponse.OfferDto;
import com.arcube.transferaggregator.dto.SearchResponse.SupplierStatusDto;
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
    
    public SearchPollingService(Optional<SlowMockSupplierClient> slowMockClient,
                                 StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper) {
        this.slowMockClient = slowMockClient.orElse(null);
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
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
    
    public SearchResponse poll(String searchId) {
        String cacheKey = CACHE_PREFIX + searchId;
        String json = redisTemplate.opsForValue().get(cacheKey);
        
        if (json == null) {
            log.warn("Poll for unknown searchId: {}", searchId);
            return SearchResponse.builder()
                .searchId(searchId)
                .offers(List.of())
                .incomplete(false)
                .supplierStatuses(Map.of())
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
        
        if (!state.isIncomplete()) {
            return buildResponse(searchId, state);
        }
        
        // Poll slow supplier
        boolean stillIncomplete = false;
        if (slowMockClient != null) {
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
        
        try {
            String updatedJson = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(cacheKey, updatedJson, CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to update cache: {}", e.getMessage());
        }
        
        log.info("Poll {}: {} offers, incomplete={}", searchId, state.getOffers().size(), stillIncomplete);
        return buildResponse(searchId, state);
    }
    
    private SearchResponse buildResponse(String searchId, SearchStateDto state) {
        return SearchResponse.builder()
            .searchId(searchId)
            .offers(new ArrayList<>(state.getOffers()))
            .incomplete(state.isIncomplete())
            .supplierStatuses(new HashMap<>(state.getStatuses()))
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
}
