package com.arcube.transferaggregator.adapters.supplier.mozio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MozioBookingRequest {
    @JsonProperty("search_id")
    private String searchId;
    
    @JsonProperty("result_id")
    private String resultId;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("first_name")
    private String firstName;
    
    @JsonProperty("last_name")
    private String lastName;
    
    @JsonProperty("phone_number")
    private String phoneNumber;
    
    @JsonProperty("country_code")
    private String countryCode;
    
    @JsonProperty("airline")
    private String airline;
    
    @JsonProperty("flight_number")
    private String flightNumber;
    
    @JsonProperty("customer_special_instructions")
    private String specialInstructions;
}
