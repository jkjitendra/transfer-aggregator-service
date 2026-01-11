package com.arcube.transferaggregator.controller;

import com.arcube.transferaggregator.dto.*;
import com.arcube.transferaggregator.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Transfer operations.
 * API documentation is defined in openapi.yaml
 */
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
    
    @GetMapping("/search/{searchId}/poll")
    public ResponseEntity<SearchResponse> poll(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String searchId) {
        log.info("Poll: searchId={}", searchId);
        return ResponseEntity.ok(pollingService.poll(searchId));
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
    
    /** Check the status of a pending cancellation.*/
    @GetMapping("/bookings/{bookingId}/cancel-status")
    public ResponseEntity<CancelResponse> getCancelStatus(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String bookingId) {
        log.info("Check cancel status: bookingId={}", bookingId);
        return ResponseEntity.ok(cancellationService.getStatus(bookingId));
    }
}

