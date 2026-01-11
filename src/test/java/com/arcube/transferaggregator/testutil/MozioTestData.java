package com.arcube.transferaggregator.testutil;

import com.arcube.transferaggregator.adapters.supplier.mozio.dto.*;
import com.arcube.transferaggregator.adapters.supplier.mozio.dto.MozioSearchResponse.*;

import java.util.List;

// Factory for creating Mozio API mock responses for testing
public final class MozioTestData {
    
    private MozioTestData() {}
    
    // ===================
    // SEARCH RESPONSES
    // ===================
    
    public static MozioSearchResponse successfulSearch(String searchId, boolean moreComing) {
        MozioSearchResponse response = new MozioSearchResponse();
        response.setSearchId(searchId);
        response.setMoreComing(moreComing);
        response.setResults(List.of(
            createSedanResult(searchId + "-sedan"),
            createSuvResult(searchId + "-suv")
        ));
        return response;
    }
    
    public static MozioSearchResponse emptySearch(String searchId) {
        MozioSearchResponse response = new MozioSearchResponse();
        response.setSearchId(searchId);
        response.setMoreComing(false);
        response.setResults(List.of());
        return response;
    }
    
    public static MozioSearchResponse pollResponse(String searchId, boolean moreComing, List<MozioResult> results) {
        MozioSearchResponse response = new MozioSearchResponse();
        response.setSearchId(searchId);
        response.setMoreComing(moreComing);
        response.setResults(results);
        return response;
    }
    
    public static MozioResult createSedanResult(String resultId) {
        MozioResult result = new MozioResult();
        result.setResultId(resultId);
        
        MozioVehicle vehicle = new MozioVehicle();
        vehicle.setMake("Toyota");
        vehicle.setModel("Camry");
        vehicle.setMaxPassengers(3);
        vehicle.setMaxBags(3);
        
        VehicleType vehicleType = new VehicleType();
        vehicleType.setKey(1);
        vehicleType.setName("sedan");
        vehicle.setVehicleType(vehicleType);
        
        MozioProvider provider = new MozioProvider();
        provider.setName("Carzen+");
        provider.setRating(4.8);
        provider.setPhone("+1234567890");
        
        MozioStepDetails details = new MozioStepDetails();
        details.setProvider(provider);
        details.setCancellation(createCancellationPolicy(true, 24, 100));
        
        MozioStep step = new MozioStep();
        step.setVehicle(vehicle);
        step.setDetails(details);
        result.setSteps(List.of(step));
        
        MozioPriceValue priceValue = new MozioPriceValue();
        priceValue.setValue(89.99);
        priceValue.setCurrency("USD");
        
        MozioPrice price = new MozioPrice();
        price.setValue(priceValue);
        result.setTotalPrice(price);
        
        return result;
    }
    
    public static MozioResult createSuvResult(String resultId) {
        MozioResult result = new MozioResult();
        result.setResultId(resultId);
        
        MozioVehicle vehicle = new MozioVehicle();
        vehicle.setMake("Ford");
        vehicle.setModel("Explorer");
        vehicle.setMaxPassengers(6);
        vehicle.setMaxBags(5);
        
        VehicleType vehicleType = new VehicleType();
        vehicleType.setKey(2);
        vehicleType.setName("suv");
        vehicle.setVehicleType(vehicleType);
        
        MozioProvider provider = new MozioProvider();
        provider.setName("Premium Rides");
        provider.setRating(4.5);
        
        MozioStepDetails details = new MozioStepDetails();
        details.setProvider(provider);
        details.setCancellation(createCancellationPolicy(true, 48, 100));
        
        MozioStep step = new MozioStep();
        step.setVehicle(vehicle);
        step.setDetails(details);
        result.setSteps(List.of(step));
        
        MozioPriceValue priceValue = new MozioPriceValue();
        priceValue.setValue(149.99);
        priceValue.setCurrency("USD");
        
        MozioPrice price = new MozioPrice();
        price.setValue(priceValue);
        result.setTotalPrice(price);
        
        return result;
    }
    
    public static MozioResult createLuxuryResult(String resultId) {
        MozioResult result = new MozioResult();
        result.setResultId(resultId);
        
        MozioVehicle vehicle = new MozioVehicle();
        vehicle.setMake("Mercedes");
        vehicle.setModel("S-Class");
        vehicle.setMaxPassengers(3);
        vehicle.setMaxBags(3);
        
        VehicleType vehicleType = new VehicleType();
        vehicleType.setKey(3);
        vehicleType.setName("luxury");
        vehicle.setVehicleType(vehicleType);
        
        MozioProvider provider = new MozioProvider();
        provider.setName("Executive Limos");
        provider.setRating(4.9);
        
        MozioStepDetails details = new MozioStepDetails();
        details.setProvider(provider);
        details.setCancellation(createCancellationPolicy(true, 72, 100));
        
        MozioStep step = new MozioStep();
        step.setVehicle(vehicle);
        step.setDetails(details);
        result.setSteps(List.of(step));
        
        MozioPriceValue priceValue = new MozioPriceValue();
        priceValue.setValue(299.99);
        priceValue.setCurrency("USD");
        
        MozioPrice price = new MozioPrice();
        price.setValue(priceValue);
        result.setTotalPrice(price);
        
        return result;
    }
    
    public static MozioResult createVanResult(String resultId) {
        MozioResult result = new MozioResult();
        result.setResultId(resultId);
        
        MozioVehicle vehicle = new MozioVehicle();
        vehicle.setMake("Mercedes");
        vehicle.setModel("Sprinter");
        vehicle.setMaxPassengers(10);
        vehicle.setMaxBags(10);
        
        VehicleType vehicleType = new VehicleType();
        vehicleType.setKey(4);
        vehicleType.setName("van");
        vehicle.setVehicleType(vehicleType);
        
        MozioProvider provider = new MozioProvider();
        provider.setName("Group Transport Co");
        provider.setRating(4.6);
        
        MozioStepDetails details = new MozioStepDetails();
        details.setProvider(provider);
        details.setCancellation(createCancellationPolicy(false, 0, 0));
        
        MozioStep step = new MozioStep();
        step.setVehicle(vehicle);
        step.setDetails(details);
        result.setSteps(List.of(step));
        
        MozioPriceValue priceValue = new MozioPriceValue();
        priceValue.setValue(199.99);
        priceValue.setCurrency("USD");
        
        MozioPrice price = new MozioPrice();
        price.setValue(priceValue);
        result.setTotalPrice(price);
        
        return result;
    }
    
    private static MozioCancellationPolicy createCancellationPolicy(boolean cancellable, int notice, int refundPercent) {
        MozioCancellationPolicy policy = new MozioCancellationPolicy();
        policy.setCancellableOnline(cancellable);
        policy.setCancellableOffline(cancellable);
        
        if (cancellable && notice > 0) {
            MozioCancellationPolicy.RefundRule rule = new MozioCancellationPolicy.RefundRule();
            rule.setNotice(notice);
            rule.setRefundPercent(refundPercent);
            policy.setPolicy(List.of(rule));
        } else {
            policy.setPolicy(List.of());
        }
        
        return policy;
    }
    
    // ===================
    // BOOKING RESPONSES
    // ===================
    
    public static MozioBookingResponse confirmedBooking(String reservationId, String confirmationNumber) {
        MozioBookingResponse response = new MozioBookingResponse();
        response.setStatus(ReservationStatus.COMPLETED);
        
        MozioBookingResponse.MozioReservation reservation = new MozioBookingResponse.MozioReservation();
        reservation.setId(reservationId);
        reservation.setConfirmationNumber(confirmationNumber);
        reservation.setStatus(ReservationStatus.COMPLETED);
        reservation.setPickupInstructions("Driver will meet you at Terminal arrivals exit.");
        
        response.setReservations(List.of(reservation));
        return response;
    }
    
    public static MozioBookingResponse pendingBooking(String reservationId) {
        MozioBookingResponse response = new MozioBookingResponse();
        response.setStatus(ReservationStatus.PENDING);
        
        MozioBookingResponse.MozioReservation reservation = new MozioBookingResponse.MozioReservation();
        reservation.setId(reservationId);
        reservation.setStatus(ReservationStatus.PENDING);
        
        response.setReservations(List.of(reservation));
        return response;
    }
    
    public static MozioBookingResponse failedBooking(String errorMessage) {
        MozioBookingResponse response = new MozioBookingResponse();
        response.setStatus(ReservationStatus.FAILED);
        
        MozioBookingResponse.MozioReservation reservation = new MozioBookingResponse.MozioReservation();
        reservation.setStatus(ReservationStatus.FAILED);
        
        response.setReservations(List.of(reservation));
        return response;
    }
}
