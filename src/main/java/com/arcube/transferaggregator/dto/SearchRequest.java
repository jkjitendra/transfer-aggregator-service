package com.arcube.transferaggregator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {
    
    @NotNull(message = "Pickup location is required")
    @Valid
    private LocationDto pickupLocation;
    
    @NotNull(message = "Dropoff location is required")
    @Valid
    private LocationDto dropoffLocation;
    
    /**
     * Pickup date/time. Optional - if not provided, Mozio will use
     * current time + their default lead time.
     */
    private LocalDateTime pickupDateTime;
    
    @Min(value = 1, message = "At least 1 passenger is required")
    private int numPassengers = 1;
    
    @Min(value = 0)
    private int numBags = 0;
    
    @Size(max = 3, message = "Currency code must be 3 characters or less")
    private String currency = "USD";
    
    private TransferModeDto mode = TransferModeDto.ONE_WAY;
    
    public enum TransferModeDto { ONE_WAY, ROUND_TRIP, HOURLY }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationDto {
        @Size(max = 500, message = "Address too long")
        private String address;
        
        @Size(max = 4, message = "IATA code must be 3-4 characters")
        private String iataCode;
        
        @Size(max = 100, message = "Place ID too long")
        private String placeId;
        
        private Double latitude;
        private Double longitude;
        
        public boolean isValid() {
            return (address != null && !address.isBlank()) ||
                   (iataCode != null && !iataCode.isBlank()) ||
                   (placeId != null) ||
                   (latitude != null && longitude != null);
        }
    }
}

