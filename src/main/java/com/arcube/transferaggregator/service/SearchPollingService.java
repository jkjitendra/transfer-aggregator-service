package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.adapters.supplier.mock.SlowMockSupplierClient;
import com.arcube.transferaggregator.config.TenantContext;
import com.arcube.transferaggregator.config.TenantConfig;
import com.arcube.transferaggregator.exception.TenantAccessException;
import com.arcube.transferaggregator.exception.SearchStateExpiredException;
import com.arcube.transferaggregator.exception.SearchStateNotFoundException;
import com.arcube.transferaggregator.exception.SearchStateUnavailableException;
import com.arcube.transferaggregator.resilience.RateLimiter;
import com.arcube.transferaggregator.observability.StorageTelemetry;
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
    private static final String KNOWN_PREFIX = "search-known:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration KNOWN_TTL = Duration.ofHours(24);
    
    private final SlowMockSupplierClient slowMockClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final OfferFilterService filterService;
    private final TenantConfig tenantConfig;
    private final RateLimiter rateLimiter;
    private final StorageTelemetry storageTelemetry;
    
    public SearchPollingService(Optional<SlowMockSupplierClient> slowMockClient,
                                 StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 OfferFilterService filterService,
                                 TenantConfig tenantConfig,
                                 Optional<RateLimiter> rateLimiter,
                                 Optional<StorageTelemetry> storageTelemetry) {
        this.slowMockClient = slowMockClient.orElse(null);
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.filterService = filterService;
        this.tenantConfig = tenantConfig;
        this.rateLimiter = rateLimiter.orElse(null);
        this.storageTelemetry = storageTelemetry.orElse(null);
    }
    
    public void cacheSearchState(String searchId, SearchResponse response, 
                                  Map<String, String> supplierSearchIds) {
        SearchStateDto state = SearchStateDto.builder()
            .searchId(searchId)
            .tenantId(TenantContext.getTenantIdOrDefault("default"))
            .offers(new ArrayList<>(response.getOffers()))
            .statuses(new HashMap<>(response.getSupplierStatuses()))
            .supplierSearchIds(new HashMap<>(supplierSearchIds))
            .incomplete(response.isIncomplete())
            .build();
        
        try {
            String cacheKey = CACHE_PREFIX + searchId;
            String json = objectMapper.writeValueAsString(state);
            set(cacheKey, json, CACHE_TTL);
            set(KNOWN_PREFIX + searchId, "1", KNOWN_TTL);
            log.debug("Cached search in Redis: id={}, incomplete={}, suppliers={}", 
                searchId, response.isIncomplete(), supplierSearchIds.keySet());
        } catch (JsonProcessingException e) {
            throw new SearchStateUnavailableException(e);
        } catch (RuntimeException e) {
            throw new SearchStateUnavailableException(e);
        }
    }
    
    /**
     * Poll with filtering, sorting, and pagination.
     */
    public SearchResponse poll(String searchId, SearchFilter filter, 
                                SearchSort sort, PageRequest page) {
        String cacheKey = CACHE_PREFIX + searchId;
        String json;
        try {
            json = get(cacheKey);
        } catch (RuntimeException e) {
            throw new SearchStateUnavailableException(e);
        }
        
        if (json == null) {
            try {
                if (get(KNOWN_PREFIX + searchId) != null) {
                    throw new SearchStateExpiredException();
                }
            } catch (SearchStateExpiredException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new SearchStateUnavailableException(e);
            }
            throw new SearchStateNotFoundException();
        }
        
        SearchStateDto state;
        try {
            state = objectMapper.readValue(json, SearchStateDto.class);
        } catch (JsonProcessingException e) {
            throw new SearchStateUnavailableException(e);
        }
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null && !tenantId.equals(state.getTenantId())) {
            throw new TenantAccessException();
        }
        if (rateLimiter != null) rateLimiter.acquirePollPermit(searchId);
        
        // Poll slow supplier if still incomplete
        boolean stillIncomplete = false;
        if (state.isIncomplete() && slowMockClient != null) {
            String slowSearchId = state.getSupplierSearchIds().get("SLOW_STUB");
            if (slowSearchId != null && slowMockClient.hasSearch(slowSearchId)) {
                SupplierSearchResult result = slowMockClient.poll(slowSearchId);
                Integer maxResults = tenantConfig.getTenant(
                    TenantContext.getTenantIdOrDefault(tenantConfig.getDefaultTenant())).getMaxResultsPerSupplier();
                List<OfferDto> visibleOffers = result.offers().stream()
                    .limit(maxResults == null ? Long.MAX_VALUE : Math.max(0, maxResults))
                    .map(this::mapToDto)
                    .toList();
                
                state.getOffers().removeIf(o -> "SLOW_STUB".equals(o.getSupplierCode()));
                state.getOffers().addAll(visibleOffers);
                
                state.getStatuses().put("SLOW_STUB", SupplierStatusDto.builder()
                    .status(result.complete() ? "SUCCESS" : "POLLING")
                    .resultsCount(visibleOffers.size())
                    .build());
                
                stillIncomplete = !result.complete();
            }
        }
        
        state.setIncomplete(stillIncomplete);
        
        // Update cache
        try {
            String updatedJson = objectMapper.writeValueAsString(state);
            set(cacheKey, updatedJson, CACHE_TTL);
        } catch (JsonProcessingException e) {
            throw new SearchStateUnavailableException(e);
        } catch (RuntimeException e) {
            throw new SearchStateUnavailableException(e);
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

    private String get(String key) {
        return storageTelemetry == null ? redisTemplate.opsForValue().get(key)
            : storageTelemetry.observe("redis", "get", () -> redisTemplate.opsForValue().get(key));
    }

    private void set(String key, String value, Duration ttl) {
        if (storageTelemetry == null) redisTemplate.opsForValue().set(key, value, ttl);
        else storageTelemetry.observe("redis", "set",
            () -> redisTemplate.opsForValue().set(key, value, ttl));
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
