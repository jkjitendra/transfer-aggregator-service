package com.arcube.transferaggregator.dto;

import com.arcube.transferaggregator.domain.Amenity;
import com.arcube.transferaggregator.domain.Money;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response with calculated total price including selected amenities.
 * Mozio returns this from /v2/pricing/
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingResponse {
    
    private String offerId;
    private Money basePrice;                    // Original offer price
    private Money amenitiesTotal;               // Sum of selected amenities
    private Money finalPrice;                   // basePrice + amenitiesTotal
    private List<SelectedAmenity> selectedAmenities;
    private String currency;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SelectedAmenity {
        private String key;
        private String name;
        private String description;
        private String imageUrl;
        private Money price;
        private boolean included;   // If included, price is 0
    }

    public static PricingResponse of(String offerId, Money basePrice, Money finalPrice, 
                                      Money amenitiesTotal, List<SelectedAmenity> amenities, String currency) {
        return PricingResponse.builder()
            .offerId(offerId)
            .basePrice(basePrice)
            .finalPrice(finalPrice)
            .amenitiesTotal(amenitiesTotal)
            .selectedAmenities(amenities)
            .currency(currency)
            .build();
    }
}
