package com.arcube.transferaggregator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sort criteria for post-aggregation sorting of search results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchSort {

    public enum SortField {
        PRICE,              // totalPrice.value
        RATING,             // provider.rating
        DURATION,           // estimatedDurationMinutes
        DISTANCE,           // distanceMeters (Mozio-aligned)
        PASSENGERS,         // vehicle.maxPassengers
        PROVIDER_NAME       // provider.name
    }

    public enum SortDirection {
        ASC, DESC
    }

    @Builder.Default
    private SortField field = SortField.PRICE;

    @Builder.Default
    private SortDirection direction = SortDirection.ASC;

    public static SearchSort byPrice() {
        return SearchSort.builder().field(SortField.PRICE).direction(SortDirection.ASC).build();
    }

    public static SearchSort byPriceDesc() {
        return SearchSort.builder().field(SortField.PRICE).direction(SortDirection.DESC).build();
    }

    public static SearchSort byRating() {
        return SearchSort.builder().field(SortField.RATING).direction(SortDirection.DESC).build();
    }

    public static SearchSort byDuration() {
        return SearchSort.builder().field(SortField.DURATION).direction(SortDirection.ASC).build();
    }
}
