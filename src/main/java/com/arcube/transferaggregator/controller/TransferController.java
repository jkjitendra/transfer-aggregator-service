package com.arcube.transferaggregator.controller;

import com.arcube.transferaggregator.dto.*;
import com.arcube.transferaggregator.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {
    
    private final TransferSearchService searchService;
    private final TransferBookingService bookingService;
    private final TransferCancellationService cancellationService;
    private final SearchPollingService pollingService;
    
    @PostMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @Valid @RequestBody SearchRequest request) {
        log.info("Search: pickup={}, dropoff={}", request.getPickupLocation(), request.getDropoffLocation());
        return ResponseEntity.ok(searchService.search(request));
    }
    
    /**
     * Poll for search results with optional filtering, sorting, and pagination.
     * Mozio-aligned: supports amenity filtering via query params.
     */
    @GetMapping("/search/{searchId}/poll")
    public ResponseEntity<SearchResponse> poll(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String searchId,
            // Pagination
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            // Sorting
            @RequestParam(defaultValue = "PRICE") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir,
            // Price filters
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            // Vehicle filters
            @RequestParam(required = false) List<String> vehicleTypes,
            @RequestParam(required = false) List<String> vehicleClasses,
            @RequestParam(required = false) List<String> vehicleCategories,
            // Capacity filters
            @RequestParam(required = false) Integer minPassengers,
            @RequestParam(required = false) Integer minBags,
            // Amenity filters (Mozio-aligned)
            @RequestParam(required = false) List<String> amenities,
            @RequestParam(required = false) Boolean freeCancellationOnly,
            // Provider filters
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) List<String> providers,
            // Duration filter
            @RequestParam(required = false) Integer maxDuration) {
        
        log.info("Poll: searchId={}, page={}, size={}, sortBy={}", searchId, page, size, sortBy);
        
        SearchFilter filter = SearchFilter.builder()
            .minPrice(minPrice)
            .maxPrice(maxPrice)
            .vehicleTypes(vehicleTypes)
            .vehicleClasses(vehicleClasses)
            .vehicleCategories(vehicleCategories)
            .minPassengers(minPassengers)
            .minBags(minBags)
            .requiredAmenities(amenities)
            .freeCancellationOnly(freeCancellationOnly)
            .minProviderRating(minRating)
            .providerNames(providers)
            .maxDurationMinutes(maxDuration)
            .build();
        
        SearchSort sort = SearchSort.builder()
            .field(parseField(sortBy))
            .direction(parseDirection(sortDir))
            .build();
        
        PageRequest pageRequest = PageRequest.of(page, size);
        
        return ResponseEntity.ok(pollingService.poll(searchId, filter, sort, pageRequest));
    }
    
    @PostMapping("/book")
    public ResponseEntity<BookResponse> book(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BookRequest request) {
        log.info("Book: offerId={}", request.getOfferId());
        return ResponseEntity.ok(bookingService.book(request, idempotencyKey));
    }
    
    @DeleteMapping("/bookings/{bookingId}")
    public ResponseEntity<CancelResponse> cancel(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String bookingId) {
        log.info("Cancel: bookingId={}", bookingId);
        return ResponseEntity.ok(cancellationService.cancel(bookingId));
    }
    
    @GetMapping("/bookings/{bookingId}/cancel-status")
    public ResponseEntity<CancelResponse> getCancelStatus(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String bookingId) {
        log.info("Check cancel status: bookingId={}", bookingId);
        return ResponseEntity.ok(cancellationService.getStatus(bookingId));
    }
    
    private SearchSort.SortField parseField(String sortBy) {
        try {
            return SearchSort.SortField.valueOf(sortBy.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SearchSort.SortField.PRICE;
        }
    }
    
    private SearchSort.SortDirection parseDirection(String sortDir) {
        try {
            return SearchSort.SortDirection.valueOf(sortDir.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SearchSort.SortDirection.ASC;
        }
    }
}
