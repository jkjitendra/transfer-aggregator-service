package com.arcube.transferaggregator.dto;

import com.arcube.transferaggregator.domain.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {
    private String searchId;
    private List<OfferDto> offers;
    private boolean incomplete;
    private Map<String, SupplierStatusDto> supplierStatuses;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OfferDto {
        private String offerId;
        private String supplierCode;
        private Vehicle vehicle;
        private Provider provider;
        private Money totalPrice;
        private CancellationPolicy cancellation;
        private int estimatedDurationMinutes;
        private boolean flightInfoRequired;
        private boolean extraPassengerInfoRequired;
        private Instant expiresAt;
        private List<String> includedAmenities;

        // Extensibility field for vendor-specific or future attributes.
        //Examples: pickup_instructions, driver_contact, etc
        private Map<String, Object> extras;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplierStatusDto {
        private String status;
        private int resultsCount;
        private String errorMessage;
    }
}
