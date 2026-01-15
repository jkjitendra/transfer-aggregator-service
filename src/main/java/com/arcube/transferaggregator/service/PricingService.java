package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.adapters.idcodec.OfferIdCodec;
import com.arcube.transferaggregator.adapters.idcodec.OfferPayload;
import com.arcube.transferaggregator.domain.Amenity;
import com.arcube.transferaggregator.domain.Money;
import com.arcube.transferaggregator.domain.Offer;
import com.arcube.transferaggregator.dto.PricingRequest;
import com.arcube.transferaggregator.dto.PricingResponse;
import com.arcube.transferaggregator.dto.PricingResponse.SelectedAmenity;
import com.arcube.transferaggregator.dto.SearchResponse.OfferDto;
import com.arcube.transferaggregator.dto.SearchStateDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for calculating total price with selected amenities.
 * Mozio endpoint: /v2/pricing/
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService {

    private static final String CACHE_PREFIX = "search:";

    private final OfferIdCodec offerIdCodec;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Calculate total price for an offer with selected amenities.
     */
    public PricingResponse calculatePrice(PricingRequest request) {
        OfferPayload payload = offerIdCodec.decode(request.getOfferId());
        String supplierCode = payload.supplierCode();
        
        // Use the aggregator's searchId from request (not the supplier's searchId from payload)
        String searchId = request.getSearchId();
        
        log.info("Calculating price for offer: supplier={}, searchId={}, amenities={}", 
            supplierCode, searchId, request.getAmenities());

        // Get cached search state to find the offer
        OfferDto offer = findOfferFromCache(searchId, request.getOfferId());
        if (offer == null) {
            log.warn("Offer not found in cache: searchId={}, offerId={}", searchId, request.getOfferId());
            return PricingResponse.builder()
                .offerId(request.getOfferId())
                .build();
        }

        Money basePrice = offer.getTotalPrice();
        String currency = basePrice != null ? basePrice.currency() : "USD";

        // Calculate amenities total
        List<SelectedAmenity> selectedAmenities = new ArrayList<>();
        BigDecimal amenitiesSum = BigDecimal.ZERO;

        if (request.getAmenities() != null && !request.getAmenities().isEmpty()) {
            // Check available amenities from offer.extras (where full amenity data would be)
            // For now, use mock amenity prices
            for (String key : request.getAmenities()) {
                AmenityInfo info = getAmenityInfo(key);
                
                boolean isIncluded = offer.getIncludedAmenities() != null && 
                    offer.getIncludedAmenities().contains(key);
                
                Money price = isIncluded ? Money.of(0, currency) : Money.of(info.price, currency);
                
                if (!isIncluded) {
                    amenitiesSum = amenitiesSum.add(BigDecimal.valueOf(info.price));
                }

                selectedAmenities.add(SelectedAmenity.builder()
                    .key(key)
                    .name(info.name)
                    .description(info.description)
                    .imageUrl(info.imageUrl)
                    .price(price)
                    .included(isIncluded)
                    .build());
            }
        }

        // Calculate final price
        BigDecimal basePriceValue = basePrice != null ? basePrice.value() : BigDecimal.ZERO;
        BigDecimal finalValue = basePriceValue.add(amenitiesSum);

        Money amenitiesTotal = Money.of(amenitiesSum, currency);
        Money finalPrice = Money.of(finalValue, currency);

        log.info("Price calculated: base={}, amenities={}, final={}", 
            basePriceValue, amenitiesSum, finalValue);

        return PricingResponse.builder()
            .offerId(request.getOfferId())
            .basePrice(basePrice)
            .amenitiesTotal(amenitiesTotal)
            .finalPrice(finalPrice)
            .selectedAmenities(selectedAmenities)
            .currency(currency)
            .build();
    }

    /**
     * Get available amenities for an offer.
     */
    public List<AmenityInfo> getAvailableAmenities(String offerId) {
        // Return all known amenities with their pricing
        // In production, this would query the supplier or use cached amenity data
        return List.of(
            new AmenityInfo("meet_and_greet", "Meet & Greet", 
                "Driver will meet you with a sign", 
                "https://static.mozio.com/amenities/meetgreet-new.svg", 30.00),
            new AmenityInfo("baby_seats", "Baby Seats (1-4 years)", 
                "Child safety seat for infants", 
                "https://static.mozio.com/amenities/baby-seats-new.svg", 15.00),
            new AmenityInfo("child_booster", "Child Booster (4-8 years)", 
                "Booster seat for young children", 
                "https://static.mozio.com/amenities/child-booster-new.svg", 15.00),
            new AmenityInfo("wifi", "WiFi", 
                "Wireless internet during the trip", 
                "https://static.mozio.com/amenities/wifi-new.svg", 5.00),
            new AmenityInfo("extra_waiting_time_thirty_min", "30 Min Extra Wait", 
                "Up to 30 minutes additional waiting time", 
                "https://static.mozio.com/amenities/waiting-new.svg", 20.00),
            new AmenityInfo("sms_notifications", "SMS Notifications", 
                "Receive SMS when driver assigned", 
                "https://static.mozio.com/amenities/sms-new.svg", 1.99)
        );
    }

    private OfferDto findOfferFromCache(String searchId, String offerId) {
        String cacheKey = CACHE_PREFIX + searchId;
        String json = redisTemplate.opsForValue().get(cacheKey);
        
        if (json == null) {
            return null;
        }

        try {
            SearchStateDto state = objectMapper.readValue(json, SearchStateDto.class);
            return state.getOffers().stream()
                .filter(o -> offerId.equals(o.getOfferId()))
                .findFirst()
                .orElse(null);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize search state: {}", e.getMessage());
            return null;
        }
    }

    private AmenityInfo getAmenityInfo(String key) {
        // Mock amenity data - in production, would come from supplier or database
        return switch (key) {
            case "meet_and_greet" -> new AmenityInfo(key, "Meet & Greet", 
                "Driver will meet you with a sign", null, 30.00);
            case "baby_seats" -> new AmenityInfo(key, "Baby Seats (1-4 years)", 
                "Child safety seat for infants", null, 15.00);
            case "child_booster" -> new AmenityInfo(key, "Child Booster (4-8 years)", 
                "Booster seat for young children", null, 15.00);
            case "wifi" -> new AmenityInfo(key, "WiFi", 
                "Wireless internet during the trip", null, 5.00);
            case "extra_waiting_time_thirty_min" -> new AmenityInfo(key, "30 Min Extra Wait", 
                "Up to 30 minutes additional waiting time", null, 20.00);
            case "extra_waiting_time_sixty_min" -> new AmenityInfo(key, "60 Min Extra Wait", 
                "Up to 60 minutes additional waiting time", null, 35.00);
            case "sms_notifications" -> new AmenityInfo(key, "SMS Notifications", 
                "Receive SMS when driver assigned", null, 1.99);
            case "ride_tracking" -> new AmenityInfo(key, "Ride Tracking", 
                "Track your ride in real-time", null, 0.00);  // Usually included
            default -> new AmenityInfo(key, key, "Additional amenity", null, 10.00);
        };
    }

    public record AmenityInfo(String key, String name, String description, String imageUrl, double price) {}
}
