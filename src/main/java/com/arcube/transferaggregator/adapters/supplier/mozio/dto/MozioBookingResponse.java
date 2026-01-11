package com.arcube.transferaggregator.adapters.supplier.mozio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MozioBookingResponse {
    @JsonProperty("status")
    private ReservationStatus status;
    
    @JsonProperty("reservations")
    private List<MozioReservation> reservations;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MozioReservation {
        @JsonProperty("id")
        private String id;
        
        @JsonProperty("confirmation_number")
        private String confirmationNumber;
        
        @JsonProperty("status")
        private ReservationStatus status;
        
        @JsonProperty("pickup_instructions")
        private String pickupInstructions;
        
        @JsonProperty("amount_paid")
        private String amountPaid;
        
        @JsonProperty("currency")
        private String currency;
        
        @JsonProperty("total_price")
        private MozioSearchResponse.MozioPrice totalPrice;
        
        @JsonProperty("gratuity")
        private String gratuity;
        
        @JsonProperty("email")
        private String email;
        
        @JsonProperty("first_name")
        private String firstName;
        
        @JsonProperty("last_name")
        private String lastName;
        
        @JsonProperty("phone_number")
        private String phoneNumber;
        
        @JsonProperty("provider")
        private ProviderDetails provider;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProviderDetails {
        private String name;
        @JsonProperty("phone_number")
        private String phoneNumber;
        private String email;
        private int rating;
        @JsonProperty("logo_url")
        private String logoUrl;
    }
}
