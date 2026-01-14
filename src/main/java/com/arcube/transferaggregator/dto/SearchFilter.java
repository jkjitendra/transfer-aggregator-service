package com.arcube.transferaggregator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Filter criteria for post-aggregation filtering of search results.
 * Extensible for additional filter types from future suppliers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchFilter {

    // Price filters
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    // Vehicle filters
    private List<String> vehicleTypes;      // sedan, SUV, minivan, van, bus
    private List<String> vehicleClasses;    // standard, business, first, luxury
    private List<String> vehicleCategories; // private, shared

    // Capacity filters
    private Integer minPassengers;
    private Integer minBags;

    // Amenity filters (Mozio-aligned: can filter by amenities in poll)
    private List<String> requiredAmenities;         // Must have all these
    private List<String> preferredAmenities;        // Nice to have (for sorting)
    private Boolean freeCancellationOnly;           // Only 100% refundable

    // Provider filters
    private BigDecimal minProviderRating;
    private List<String> providerNames;             // Include only these providers

    // Time filters
    private Integer maxDurationMinutes;

    // Supplier filter (internal use)
    private List<String> supplierCodes;

    public boolean hasFilters() {
        return minPrice != null || maxPrice != null ||
               (vehicleTypes != null && !vehicleTypes.isEmpty()) ||
               (vehicleClasses != null && !vehicleClasses.isEmpty()) ||
               (vehicleCategories != null && !vehicleCategories.isEmpty()) ||
               minPassengers != null || minBags != null ||
               (requiredAmenities != null && !requiredAmenities.isEmpty()) ||
               freeCancellationOnly != null ||
               minProviderRating != null ||
               (providerNames != null && !providerNames.isEmpty()) ||
               maxDurationMinutes != null ||
               (supplierCodes != null && !supplierCodes.isEmpty());
    }
}
