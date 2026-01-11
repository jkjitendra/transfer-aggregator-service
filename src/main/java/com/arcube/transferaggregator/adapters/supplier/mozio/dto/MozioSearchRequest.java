package com.arcube.transferaggregator.adapters.supplier.mozio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MozioSearchRequest {
    @JsonProperty("start_address")
    private String startAddress;
    
    @JsonProperty("end_address")
    private String endAddress;
    
    @JsonProperty("mode")
    private String mode; // "one_way", "round_trip"
    
    @JsonProperty("pickup_datetime")
    private String pickupDatetime; // ISO 8601
    
    @JsonProperty("num_passengers")
    private int numPassengers;
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("campaign")
    private String campaign;
}
