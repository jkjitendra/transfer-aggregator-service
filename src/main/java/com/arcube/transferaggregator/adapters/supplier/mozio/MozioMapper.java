package com.arcube.transferaggregator.adapters.supplier.mozio;

import com.arcube.transferaggregator.adapters.idcodec.OfferIdCodec;
import com.arcube.transferaggregator.adapters.idcodec.OfferPayload;
import com.arcube.transferaggregator.adapters.supplier.mozio.dto.MozioSearchResponse.*;
import com.arcube.transferaggregator.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

// Maps Mozio DTOs to domain objects
@Component
@RequiredArgsConstructor
public class MozioMapper {
    
    private final OfferIdCodec offerIdCodec;
    private static final String SUPPLIER_CODE = "MOZIO";
    
    // Converts Mozio search result to our Offer domain object
    public Offer mapToOffer(MozioResult result, String searchId, Instant expiresAt) {
        OfferPayload payload = OfferPayload.of(SUPPLIER_CODE, searchId, result.getResultId(), expiresAt);
        String offerId = offerIdCodec.encode(payload);
        
        var steps = result.getSteps();
        var mainStep = findMainStep(steps);
        
        var vehicle = mainStep != null ? mainStep.getVehicle() : null;
        var details = mainStep != null ? mainStep.getDetails() : null;
        var detailsVehicle = details != null ? details.getVehicle() : null;
        var provider = details != null ? details.getProvider() : null;
        
        // Use vehicle from details if main step vehicle is null
        var effectiveVehicle = vehicle != null ? vehicle : detailsVehicle;
        
        return Offer.builder()
            .offerId(offerId)
            .supplierCode(SUPPLIER_CODE)
            .vehicle(mapVehicle(effectiveVehicle))
            .provider(mapProvider(provider))
            .totalPrice(mapPrice(result.getTotalPrice()))
            .cancellation(mapCancellationPolicy(details != null ? details.getCancellation() : null))
            .estimatedDurationMinutes(details != null && details.getTime() != null ? details.getTime() : 0)
            .flightInfoRequired(details != null && details.isFlightInfoRequired())
            .extraPassengerInfoRequired(details != null && details.isExtraPaxRequired())
            .expiresAt(expiresAt)
            .includedAmenities(mapAmenities(details != null ? details.getAmenities() : null))
            .extras(buildExtras(result, mainStep, details))
            .build();
    }
    
    private MozioStep findMainStep(List<MozioStep> steps) {
        if (steps == null || steps.isEmpty()) return null;
        return steps.stream()
            .filter(MozioStep::isMain)
            .findFirst()
            .orElse(steps.get(0));
    }
    
    private Vehicle mapVehicle(MozioVehicle v) {
        if (v == null) {
            return Vehicle.builder()
                .type("Unknown")
                .maxPassengers(4)
                .maxBags(2)
                .build();
        }
        
        String type = v.getVehicleType() != null ? v.getVehicleType().getName() : "Unknown";
        String category = v.getCategory() != null ? v.getCategory().getName() : null;
        String vehicleClass = v.getVehicleClassDetail() != null ? v.getVehicleClassDetail().getDisplayName() : null;
        
        return Vehicle.builder()
            .type(type)
            .make(v.getMake())
            .model(v.getModel())
            .category(category)
            .vehicleClass(vehicleClass)
            .maxPassengers(v.getMaxPassengers())
            .maxBags(v.getMaxBags())
            .build();
    }
    
    private Provider mapProvider(MozioProvider p) {
        if (p == null) {
            return Provider.builder()
                .name("Unknown Provider")
                .displayName("Unknown Provider")
                .build();
        }
        
        return Provider.builder()
            .name(p.getName())
            .displayName(p.getDisplayName() != null ? p.getDisplayName() : p.getName())
            .logoUrl(p.getLogoUrl())
            .rating(BigDecimal.valueOf(p.getRating()))
            .ratingCount(p.getRatingCount())
            .contactPhone(p.getPhoneNumber() != null ? p.getPhoneNumber() : p.getPhone())
            .build();
    }
    
    private Money mapPrice(MozioPrice price) {
        if (price == null || price.getValue() == null) {
            return Money.of(0, "USD");
        }
        String currency = price.getValue().getCurrency() != null ? price.getValue().getCurrency() : "USD";
        return Money.of(price.getValue().getValue(), currency);
    }
    
    private List<Amenity> mapAmenities(List<MozioAmenity> amenities) {
        if (amenities == null || amenities.isEmpty()) return List.of();
        
        return amenities.stream()
            .filter(a -> a.isIncluded() || a.isSelected())
            .map(a -> Amenity.included(a.getKey(), a.getName()))
            .toList();
    }
    
    private CancellationPolicy mapCancellationPolicy(MozioCancellationPolicy policy) {
        if (policy == null) return null;
        
        List<CancellationPolicy.CancellationTier> tiers = new ArrayList<>();
        if (policy.getPolicy() != null) {
            for (var rule : policy.getPolicy()) {
                tiers.add(new CancellationPolicy.CancellationTier(rule.getNotice(), rule.getRefundPercent()));
            }
        }
        
        return CancellationPolicy.builder()
            .cancellableOnline(policy.isCancellableOnline())
            .cancellableOffline(policy.isCancellableOffline())
            .tiers(tiers)
            .build();
    }
    
    // Builds extras map with any Mozio-specific data useful for frontend
    private Map<String, Object> buildExtras(MozioResult result, MozioStep mainStep, MozioStepDetails details) {
        Map<String, Object> extras = new HashMap<>();
        
        // Vehicle image
        var vehicle = mainStep != null ? mainStep.getVehicle() : null;
        if (vehicle == null && details != null) vehicle = details.getVehicle();
        if (vehicle != null && vehicle.getImage() != null) {
            extras.put("vehicle_image_url", vehicle.getImage());
        }
        
        // Wait time info
        if (details != null && details.getWaitTime() != null) {
            extras.put("wait_time_minutes_included", details.getWaitTime().getMinutesIncluded());
        }
        
        // Provider contact
        if (details != null && details.getProvider() != null) {
            var provider = details.getProvider();
            if (provider.getPhoneNumber() != null) {
                extras.put("provider_phone", provider.getPhoneNumber());
            }
            if (provider.getLogoUrl() != null) {
                extras.put("provider_logo_url", provider.getLogoUrl());
            }
        }
        
        // Price breakdown
        if (details != null && details.getPrice() != null) {
            extras.put("tolls_included", details.getPrice().isTollsIncluded());
            extras.put("gratuity_included", details.getPrice().isGratuityIncluded());
            extras.put("gratuity_accepted", details.getPrice().isGratuityAccepted());
        }
        
        // Terms URL
        if (details != null && details.getTermsUrl() != null && !details.getTermsUrl().isEmpty()) {
            extras.put("terms_url", details.getTermsUrl());
        }
        
        // Supports tracking
        if (result.getSupports() != null && Boolean.TRUE.equals(result.getSupports().getTracking())) {
            extras.put("supports_tracking", true);
        }
        
        // Good to know info
        if (result.getGoodToKnowInfo() != null) {
            extras.put("good_to_know_info", result.getGoodToKnowInfo());
        }
        
        // All amenities (both included and optional)
        if (details != null && details.getAmenities() != null && !details.getAmenities().isEmpty()) {
            List<Map<String, Object>> amenityList = details.getAmenities().stream()
                .map(a -> {
                    Map<String, Object> am = new HashMap<>();
                    am.put("key", a.getKey());
                    am.put("name", a.getName());
                    am.put("included", a.isIncluded());
                    if (a.getPrice() != null) {
                        am.put("price", a.getPrice().getValue());
                    }
                    return am;
                })
                .toList();
            extras.put("all_amenities", amenityList);
        }
        
        return extras.isEmpty() ? null : extras;
    }
}
