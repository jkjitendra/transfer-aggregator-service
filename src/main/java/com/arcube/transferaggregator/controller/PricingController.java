package com.arcube.transferaggregator.controller;

import com.arcube.transferaggregator.dto.PricingRequest;
import com.arcube.transferaggregator.dto.PricingResponse;
import com.arcube.transferaggregator.service.PricingService;
import com.arcube.transferaggregator.service.PricingService.AmenityInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for pricing operations.
 * Mozio endpoint: /v2/pricing/
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;

    /**
     * Calculate total price for an offer with selected amenities.
     * POST /api/v1/pricing
     */
    @PostMapping
    public ResponseEntity<PricingResponse> calculatePrice(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @Valid @RequestBody PricingRequest request) {
        log.info("Calculate price: offerId={}, amenities={}", 
            request.getOfferId(), request.getAmenities());
        return ResponseEntity.ok(pricingService.calculatePrice(request));
    }

    /**
     * Get total price with amenities via query params (Mozio GET support).
     * GET /api/v1/pricing?searchId=xxx&offerId=xxx&amenities=baby_seats&amenities=wifi
     */
    @GetMapping
    public ResponseEntity<PricingResponse> getPriceWithAmenities(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam String searchId,
            @RequestParam String offerId,
            @RequestParam(required = false) List<String> amenities) {
        log.info("Get price: searchId={}, offerId={}, amenities={}", searchId, offerId, amenities);
        
        PricingRequest request = PricingRequest.builder()
            .searchId(searchId)
            .offerId(offerId)
            .amenities(amenities)
            .build();
        
        return ResponseEntity.ok(pricingService.calculatePrice(request));
    }

    /**
     * Get list of available amenities for an offer.
     * GET /api/v1/pricing/{offerId}/amenities
     */
    @GetMapping("/{offerId}/amenities")
    public ResponseEntity<List<AmenityInfo>> getAvailableAmenities(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String offerId) {
        log.info("Get available amenities: offerId={}", offerId);
        return ResponseEntity.ok(pricingService.getAvailableAmenities(offerId));
    }
}
