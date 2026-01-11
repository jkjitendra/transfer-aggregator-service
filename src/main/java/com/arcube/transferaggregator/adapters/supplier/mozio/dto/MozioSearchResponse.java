package com.arcube.transferaggregator.adapters.supplier.mozio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MozioSearchResponse {
    @JsonProperty("search_id")
    private String searchId;
    
    @JsonProperty("results")
    private List<MozioResult> results;
    
    @JsonProperty("more_coming")
    private boolean moreComing;
    
    @JsonProperty("expires_at")
    private Long expiresAt;
    
    @JsonProperty("created_at")
    private Long createdAt;
    
    @JsonProperty("num_passengers")
    private Integer numPassengers;
    
    @JsonProperty("pickup_datetime")
    private String pickupDatetime;
    
    @JsonProperty("currency_info")
    private CurrencyInfo currencyInfo;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CurrencyInfo {
        private String code;
        @JsonProperty("prefix_symbol")
        private String prefixSymbol;
        @JsonProperty("suffix_symbol")
        private String suffixSymbol;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MozioResult {
        @JsonProperty("result_id")
        private String resultId;
        
        @JsonProperty("vehicle_id")
        private String vehicleId;
        
        @JsonProperty("steps")
        private List<MozioStep> steps;
        
        @JsonProperty("total_price")
        private MozioPrice totalPrice;
        
        @JsonProperty("tags")
        private List<String> tags;
        
        @JsonProperty("loyalty")
        private Object loyalty;
        
        @JsonProperty("supports")
        private Supports supports;
        
        @JsonProperty("good_to_know_info")
        private String goodToKnowInfo;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Supports {
        private Boolean snapping;
        private Boolean tracking;
        private Boolean coupon;
        @JsonProperty("vehicle_and_driver")
        private Boolean vehicleAndDriver;
        @JsonProperty("buffer_time")
        private Boolean bufferTime;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MozioStep {
        private boolean main;
        
        @JsonProperty("step_type")
        private String stepType;
        
        @JsonProperty("vehicle")
        private MozioVehicle vehicle;
        
        @JsonProperty("details")
        private MozioStepDetails details;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MozioStepDetails {
        private String description;
        private String notes;
        
        @JsonProperty("provider")
        private MozioProvider provider;
        
        @JsonProperty("provider_name")
        private String providerName;
        
        private Integer time;
        
        @JsonProperty("departure_datetime")
        private String departureDatetime;
        
        @JsonProperty("vehicle")
        private MozioVehicle vehicle;
        
        @JsonProperty("price")
        private StepPrice price;
        
        @JsonProperty("cancellation")
        private MozioCancellationPolicy cancellation;
        
        @JsonProperty("wait_time")
        private WaitTime waitTime;
        
        @JsonProperty("amenities")
        private List<MozioAmenity> amenities;
        
        @JsonProperty("flight_info_required")
        private boolean flightInfoRequired;
        
        @JsonProperty("extra_pax_required")
        private boolean extraPaxRequired;
        
        @JsonProperty("bookable")
        private boolean bookable;
        
        @JsonProperty("terms_url")
        private String termsUrl;
        
        @JsonProperty("maximum_pickup_time_buffer")
        private Integer maximumPickupTimeBuffer;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StepPrice {
        private MozioPriceValue price;
        @JsonProperty("tolls_included")
        private boolean tollsIncluded;
        @JsonProperty("gratuity_included")
        private boolean gratuityIncluded;
        @JsonProperty("gratuity_accepted")
        private boolean gratuityAccepted;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WaitTime {
        @JsonProperty("minutes_included")
        private int minutesIncluded;
        @JsonProperty("waiting_minute_price")
        private MozioPriceValue waitingMinutePrice;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MozioAmenity {
        private String key;
        private String name;
        private String description;
        @JsonProperty("image_url")
        private String imageUrl;
        @JsonProperty("png_image_url")
        private String pngImageUrl;
        @JsonProperty("input_type")
        private String inputType;
        private boolean included;
        private boolean selected;
        private MozioPriceValue price;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MozioVehicle {
        private String make;
        private String model;
        private String image;
        
        @JsonProperty("vehicle_type")
        private VehicleType vehicleType;
        
        @JsonProperty("vehicle_class")
        private Integer vehicleClass;
        
        @JsonProperty("vehicle_class_detail")
        private VehicleClassDetail vehicleClassDetail;
        
        @JsonProperty("category")
        private VehicleCategory category;
        
        @JsonProperty("max_passengers")
        private int maxPassengers;
        
        @JsonProperty("max_bags")
        private int maxBags;
        
        @JsonProperty("is_max_bags_per_person")
        private Boolean isMaxBagsPerPerson;
        
        @JsonProperty("num_vehicles")
        private int numVehicles;
        
        @JsonProperty("total_bags")
        private Integer totalBags;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VehicleType {
        private int key;
        private String name;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VehicleCategory {
        private int id;
        private String name;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VehicleClassDetail {
        @JsonProperty("display_name")
        private String displayName;
        @JsonProperty("vehicle_class_id")
        private Integer vehicleClassId;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MozioProvider {
        private String name;
        @JsonProperty("display_name")
        private String displayName;
        @JsonProperty("logo_url")
        private String logoUrl;
        private double rating;
        @JsonProperty("rating_count")
        private Integer ratingCount;
        @JsonProperty("rating_with_decimals")
        private String ratingWithDecimals;
        @JsonProperty("supplier_score")
        private Double supplierScore;
        @JsonProperty("phone_number")
        private String phoneNumber;
        private String phone;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MozioPrice {
        @JsonProperty("total_price")
        private MozioPriceValue value;
        
        @JsonProperty("total_price_without_platform_fee")
        private MozioPriceValue totalPriceWithoutPlatformFee;
        
        @JsonProperty("platform_fee")
        private MozioPriceValue platformFee;
        
        @JsonProperty("slashed_price")
        private MozioPriceValue slashedPrice;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MozioPriceValue {
        private double value;
        private String display;
        private String compact;
        private String currency;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MozioCancellationPolicy {
        @JsonProperty("cancellable_online")
        private boolean cancellableOnline;
        
        @JsonProperty("cancellable_offline")
        private boolean cancellableOffline;
        
        private boolean amendable;
        
        @JsonProperty("policy")
        private List<RefundRule> policy;
        
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class RefundRule {
            @JsonProperty("notice")
            private int notice;
            @JsonProperty("refund_percent")
            private int refundPercent;
        }
    }
}
