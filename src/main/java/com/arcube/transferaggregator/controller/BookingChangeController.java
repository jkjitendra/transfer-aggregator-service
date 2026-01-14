package com.arcube.transferaggregator.controller;

import com.arcube.transferaggregator.dto.*;
import com.arcube.transferaggregator.service.TransferBookingChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/transfers/bookings")
@RequiredArgsConstructor
public class BookingChangeController {

    private final TransferBookingChangeService bookingChangeService;

    /**
     * Search for alternative options to change an existing booking.
     * Returns available offers based on new parameters.
     */
    @PostMapping("/{bookingId}/search-changes")
    public ResponseEntity<SearchResponse> searchForChange(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String bookingId,
            @Valid @RequestBody BookingChangeSearchRequest request) {
        log.info("Search changes: bookingId={}", bookingId);
        request.setBookingId(bookingId);
        return ResponseEntity.ok(bookingChangeService.searchForChange(request));
    }

    /**
     * Commit a booking change by selecting a new offer from the search results.
     * This will cancel the old booking and create a new one.
     */
    @PostMapping("/{bookingId}/commit-change")
    public ResponseEntity<BookingChangeResponse> commitChange(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable String bookingId,
            @Valid @RequestBody BookingChangeCommitRequest request) {
        log.info("Commit change: oldBookingId={}, newResultId={}", bookingId, request.getResultId());
        request.setOldBookingId(bookingId);
        return ResponseEntity.ok(bookingChangeService.commitChange(request, idempotencyKey));
    }
}
