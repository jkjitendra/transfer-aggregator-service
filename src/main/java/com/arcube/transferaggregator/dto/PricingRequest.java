package com.arcube.transferaggregator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request for calculating total price with selected amenities.
 * Mozio endpoint: POST /v2/pricing/
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingRequest {
    
    private String searchId;            // Aggregator's searchId (for cache lookup)
    private String offerId;             // Encoded offer ID (contains searchId + resultId)
    private List<String> amenities;     // Selected amenity keys (e.g., ["baby_seats", "meet_and_greet"])
}
