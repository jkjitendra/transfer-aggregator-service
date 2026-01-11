package com.arcube.transferaggregator.adapters.supplier.mozio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// Mozio reservation status enum - used in booking response
public enum ReservationStatus {
    @JsonProperty("pending")
    PENDING,
    
    @JsonProperty("completed")
    COMPLETED,
    
    @JsonProperty("failed")
    FAILED
}
