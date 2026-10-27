package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.config.AggregatorProperties;
import com.arcube.transferaggregator.config.TenantConfig;
import com.arcube.transferaggregator.config.TenantContext;
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
import com.arcube.transferaggregator.resilience.SupplierCircuitBreaker;
import com.arcube.transferaggregator.resilience.SupplierOperationPolicy;
import com.arcube.transferaggregator.observability.SupplierMetrics;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferSearchService {
    
    private final SupplierRegistry supplierRegistry;
    private final AggregatorProperties properties;
    private final SupplierBulkhead bulkhead;
    private final RateLimiter rateLimiter;
    private final SearchPollingService pollingService;
    private final SupplierCircuitBreaker circuitBreaker;
    private final TenantConfig tenantConfig;
    @Autowired(required = false)
    private SupplierMetrics supplierMetrics;
    @Autowired(required = false)
    private ObservationRegistry observations;
    
    public SearchResponse search(SearchRequest request) {
        long searchStarted = System.nanoTime();
        var searchId = UUID.randomUUID().toString();
        var deadline = Instant.now().plusSeconds(properties.getGlobalTimeoutSeconds());
        
        // Get current tenant from context
        String tenantId = TenantContext.getTenantIdOrDefault(tenantConfig.getDefaultTenant());
        var tenant = tenantConfig.getTenant(tenantId);
        var command = mapToCommand(request, tenant);
        
        // Filter suppliers by tenant configuration
        List<TransferSupplier> suppliers = supplierRegistry.getEnabledSuppliers().stream()
            .filter(s -> tenantConfig.isSupplierEnabled(tenantId, s.getSupplierCode()))
            .toList();
        
        log.info("Search {}: tenant={}, {} suppliers enabled, bulkhead permits={}", 
            searchId, tenantId, suppliers.size(), bulkhead.availablePermits());
        
        if (suppliers.isEmpty()) {
            recordRequest(true, searchStarted);
            return SearchResponse.builder()
                .searchId(searchId).offers(List.of()).incomplete(true).supplierStatuses(Map.of())
                .build();
        }
        if (!Instant.now().isBefore(deadline)) {
            Map<String, SupplierStatusDto> timedOut = new LinkedHashMap<>();
            for (TransferSupplier supplier : suppliers) {
                timedOut.put(supplier.getSupplierCode(), SupplierStatusDto.builder()
                    .status("TIMEOUT").errorMessage("Search deadline exceeded").build());
            }
            recordRequest(true, searchStarted);
            return SearchResponse.builder().searchId(searchId).offers(List.of())
                .incomplete(true).supplierStatuses(timedOut).build();
        }
        
        // Fan out to suppliers in parallel with bulkhead and circuit breaker protection
        Map<String, Future<SupplierSearchResult>> futures = new LinkedHashMap<>();
        Map<String, Instant> supplierDeadlines = new HashMap<>();
        Map<String, Long> supplierStarts = new HashMap<>();
        Set<String> deadlineSkipped = new HashSet<>();
        Map<String, String> requestContext = MDC.getCopyOfContextMap();
        Observation parent = observations == null ? null : observations.getCurrentObservation();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            for (TransferSupplier supplier : suppliers) {
                String code = supplier.getSupplierCode();
            
                // Skip if circuit breaker is open
                if (circuitBreaker.isOpen(code)) {
                    log.warn("Skipping supplier {} - circuit breaker OPEN", code);
                    continue;
                }
                if (!Instant.now().isBefore(deadline)) {
                    deadlineSkipped.add(code);
                    continue;
                }
            
                Duration timeout = SupplierOperationPolicy.timeout(properties, code,
                    SupplierOperationPolicy.Operation.SEARCH, Duration.between(Instant.now(), deadline));
                supplierDeadlines.put(code, Instant.now().plus(timeout));
                supplierStarts.put(code, System.nanoTime());
            
                futures.put(code, executor.submit(() -> {
                    try {
                        if (requestContext != null) MDC.setContextMap(requestContext);
                        TenantContext.setTenantId(tenantId);
                        java.util.function.Supplier<SupplierSearchResult> call = () -> {
                            rateLimiter.acquireSearchPermit(code);
                            return circuitBreaker.execute(
                                code,
                                () -> bulkhead.execute(() -> supplier.search(command, timeout)),
                                () -> { throw new IllegalStateException("Supplier circuit unavailable"); }
                            );
                        };
                        if (observations == null) return call.get();
                        return Observation.createNotStarted("transfer.supplier.search", observations)
                            .parentObservation(parent).lowCardinalityKeyValue("supplier", code)
                            .observe(call::get);
                    } finally {
                        TenantContext.clear();
                        MDC.clear();
                    }
                }));
            }
        
            // Gather results
            List<Offer> allOffers = new ArrayList<>();
            Map<String, SupplierStatusDto> statuses = new HashMap<>();
            Map<String, String> supplierSearchIds = new HashMap<>();
            boolean incomplete = false;
        
            // Mark skipped suppliers
            for (TransferSupplier supplier : suppliers) {
                String code = supplier.getSupplierCode();
                if (!futures.containsKey(code)) {
                    statuses.put(code, SupplierStatusDto.builder()
                        .status(deadlineSkipped.contains(code) ? "TIMEOUT" : "CIRCUIT_OPEN")
                        .errorMessage(deadlineSkipped.contains(code) ? "Search deadline exceeded"
                            : "Circuit breaker open - supplier temporarily unavailable")
                        .build());
                    incomplete = true;
                }
            }
        
            for (var entry : futures.entrySet()) {
                String code = entry.getKey();
                try {
                    Duration remaining = Duration.between(Instant.now(), deadline);
                    Duration supplierRemaining = Duration.between(Instant.now(), supplierDeadlines.get(code));
                    if (supplierRemaining.compareTo(remaining) < 0) remaining = supplierRemaining;
                    if (remaining.isNegative() || remaining.isZero()) throw new TimeoutException();
                    SupplierSearchResult result = entry.getValue().get(remaining.toMillis(), TimeUnit.MILLISECONDS);
                    if (result == null || result.offers() == null) {
                        throw new IllegalStateException("Malformed supplier search response");
                    }
                    List<Offer> visibleOffers = tenant.getMaxResultsPerSupplier() == null
                        ? result.offers()
                        : result.offers().stream().limit(Math.max(0, tenant.getMaxResultsPerSupplier())).toList();
                    allOffers.addAll(visibleOffers);
                    supplierSearchIds.put(code, result.searchId());
                
                    String status = result.complete() ? "SUCCESS" : "POLLING";
                    if (result.timedOut()) status = "TIMEOUT";
                
                    statuses.put(code, SupplierStatusDto.builder()
                        .status(status)
                        .resultsCount(visibleOffers.size()).build());
                    if (!result.complete() || result.timedOut()) incomplete = true;
                    log.info("Supplier {} returned {} offers, complete={}", code, result.offers().size(), result.complete());
                } catch (TimeoutException e) {
                    entry.getValue().cancel(true);
                    statuses.put(code, SupplierStatusDto.builder().status("TIMEOUT")
                        .errorMessage("Supplier deadline exceeded").build());
                    incomplete = true;
                } catch (InterruptedException e) {
                    entry.getValue().cancel(true);
                    Thread.currentThread().interrupt();
                    statuses.put(code, SupplierStatusDto.builder().status("TIMEOUT")
                        .errorMessage("Request interrupted").build());
                    incomplete = true;
                    break;
                } catch (ExecutionException | CancellationException e) {
                    log.warn("Supplier {} failed: {}", code, e.getClass().getSimpleName());
                    statuses.put(code, SupplierStatusDto.builder().status("ERROR")
                        .errorMessage("Supplier temporarily unavailable").build());
                    incomplete = true;
                } catch (RuntimeException e) {
                    log.warn("Supplier {} returned an unusable response", code);
                    statuses.put(code, SupplierStatusDto.builder().status("ERROR")
                        .errorMessage("Supplier temporarily unavailable").build());
                    incomplete = true;
                }
            }

            for (var entry : futures.entrySet()) {
                if (!statuses.containsKey(entry.getKey())) {
                    entry.getValue().cancel(true);
                    statuses.put(entry.getKey(), SupplierStatusDto.builder().status("TIMEOUT")
                        .errorMessage("Request interrupted").build());
                    incomplete = true;
                }
            }
            if (supplierMetrics != null) {
                for (var entry : statuses.entrySet()) {
                    long began = supplierStarts.getOrDefault(entry.getKey(), searchStarted);
                    supplierMetrics.recordSearchOutcome(entry.getKey(), entry.getValue().getStatus(),
                        Duration.ofNanos(Math.max(0, System.nanoTime() - began)));
                }
            }
        
            log.info("Search {} complete: {} offers, incomplete={}", searchId, allOffers.size(), incomplete);
            SearchResponse response = SearchResponse.builder()
                .searchId(searchId)
                .offers(allOffers.stream().map(this::mapToOfferDto).toList())
                .incomplete(incomplete)
                .supplierStatuses(statuses)
                .build();
        
            if (incomplete) {
                pollingService.cacheSearchState(searchId, response, supplierSearchIds);
            }

            recordRequest(incomplete, searchStarted);
        
            return response;
        } finally {
            futures.values().forEach(future -> { if (!future.isDone()) future.cancel(true); });
            executor.shutdownNow();
        }
    }

    private void recordRequest(boolean partial, long started) {
        if (supplierMetrics != null) {
            supplierMetrics.recordSearchRequest(partial,
                Duration.ofNanos(Math.max(0, System.nanoTime() - started)));
        }
    }
    
    private SearchCommand mapToCommand(SearchRequest req, AggregatorProperties.TenantProperties tenant) {
        return SearchCommand.builder()
            .pickupLocation(mapLocation(req.getPickupLocation()))
            .dropoffLocation(mapLocation(req.getDropoffLocation()))
            .pickupDateTime(req.getPickupDateTime())
            .numPassengers(req.getNumPassengers())
            .numBags(req.getNumBags())
            .currency(req.getCurrency() == null || req.getCurrency().isBlank() ? tenant.getDefaultCurrency() : req.getCurrency())
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
            .distanceMeters(o.distanceMeters())
            .flightInfoRequired(o.flightInfoRequired()).extraPassengerInfoRequired(o.extraPassengerInfoRequired())
            .expiresAt(o.expiresAt())
            .includedAmenities(o.includedAmenities() != null ? o.includedAmenities().stream().map(Amenity::key).toList() : List.of())
            .extras(o.extras())
            .build();
    }
}
