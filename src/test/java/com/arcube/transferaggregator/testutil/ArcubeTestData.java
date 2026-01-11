package com.arcube.transferaggregator.testutil;

import com.arcube.transferaggregator.adapters.idcodec.BookingPayload;
import com.arcube.transferaggregator.adapters.idcodec.OfferPayload;
import com.arcube.transferaggregator.dto.BookRequest;
import com.arcube.transferaggregator.dto.SearchRequest;
import com.arcube.transferaggregator.dto.SearchRequest.LocationDto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Factory for creating test data for Arcube API tests.
 * All methods return valid, well-formed objects unless noted otherwise.
 */
public final class ArcubeTestData {
    
    private ArcubeTestData() {}
    
    // ===================
    // SEARCH REQUESTS
    // ===================
    
    public static SearchRequest validAddressSearch() {
        return SearchRequest.builder()
            .pickupLocation(new LocationDto("433 Park Avenue, New York, NY 10022", null, null, null, null))
            .dropoffLocation(new LocationDto("JFK Airport, Queens, NY", null, null, null, null))
            .pickupDateTime(LocalDateTime.now().plusDays(7).withHour(15).withMinute(30))
            .numPassengers(2)
            .numBags(2)
            .currency("USD")
            .mode(SearchRequest.TransferModeDto.ONE_WAY)
            .build();
    }
    
    public static SearchRequest validIataSearch() {
        return SearchRequest.builder()
            .pickupLocation(new LocationDto("Times Square, NYC", null, null, null, null))
            .dropoffLocation(new LocationDto(null, "JFK", null, null, null))
            .pickupDateTime(LocalDateTime.now().plusDays(14).withHour(10).withMinute(0))
            .numPassengers(3)
            .numBags(4)
            .currency("USD")
            .mode(SearchRequest.TransferModeDto.ONE_WAY)
            .build();
    }
    
    public static SearchRequest validCoordinatesSearch() {
        return SearchRequest.builder()
            .pickupLocation(new LocationDto(null, null, null, 40.7580, -73.9855))
            .dropoffLocation(new LocationDto(null, null, null, 40.6413, -73.7781))
            .pickupDateTime(LocalDateTime.now().plusDays(30).withHour(14).withMinute(0))
            .numPassengers(1)
            .numBags(1)
            .currency("EUR")
            .mode(SearchRequest.TransferModeDto.ONE_WAY)
            .build();
    }
    
    public static SearchRequest roundTripSearch() {
        return SearchRequest.builder()
            .pickupLocation(new LocationDto(null, "LAX", null, null, null))
            .dropoffLocation(new LocationDto("Beverly Hills, CA 90210", null, null, null, null))
            .pickupDateTime(LocalDateTime.now().plusDays(60).withHour(9).withMinute(0))
            .numPassengers(4)
            .numBags(6)
            .currency("USD")
            .mode(SearchRequest.TransferModeDto.ROUND_TRIP)
            .build();
    }
    
    public static SearchRequest largeGroupSearch() {
        return SearchRequest.builder()
            .pickupLocation(new LocationDto(null, "ORD", null, null, null))
            .dropoffLocation(new LocationDto("Downtown Chicago, IL", null, null, null, null))
            .pickupDateTime(LocalDateTime.now().plusDays(45).withHour(18).withMinute(0))
            .numPassengers(8)
            .numBags(10)
            .currency("USD")
            .mode(SearchRequest.TransferModeDto.ONE_WAY)
            .build();
    }
    
    // Invalid requests for validation testing
    public static SearchRequest missingPickup() {
        return SearchRequest.builder()
            .dropoffLocation(new LocationDto(null, "JFK", null, null, null))
            .pickupDateTime(LocalDateTime.now().plusDays(7))
            .numPassengers(2)
            .build();
    }
    
    public static SearchRequest missingDropoff() {
        return SearchRequest.builder()
            .pickupLocation(new LocationDto("123 Main St", null, null, null, null))
            .pickupDateTime(LocalDateTime.now().plusDays(7))
            .numPassengers(2)
            .build();
    }
    
    public static SearchRequest zeroPassengers() {
        return SearchRequest.builder()
            .pickupLocation(new LocationDto("123 Main St", null, null, null, null))
            .dropoffLocation(new LocationDto(null, "JFK", null, null, null))
            .pickupDateTime(LocalDateTime.now().plusDays(7))
            .numPassengers(0)
            .build();
    }
    
    public static SearchRequest addressTooLong() {
        String longAddress = "A".repeat(501);
        return SearchRequest.builder()
            .pickupLocation(new LocationDto(longAddress, null, null, null, null))
            .dropoffLocation(new LocationDto(null, "JFK", null, null, null))
            .pickupDateTime(LocalDateTime.now().plusDays(7))
            .numPassengers(2)
            .build();
    }
    
    // ===================
    // BOOKING REQUESTS
    // ===================
    
    public static BookRequest validBooking(String offerId) {
        return BookRequest.builder()
            .offerId(offerId)
            .passenger(BookRequest.PassengerDto.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .phoneNumber("+18005551234")
                .countryCode("US")
                .build())
            .flight(BookRequest.FlightDto.builder()
                .airline("AA")
                .flightNumber("123")
                .build())
            .specialInstructions("Please wait at arrivals exit 3")
            .build();
    }
    
    public static BookRequest bookingWithExtraPassengers(String offerId) {
        return BookRequest.builder()
            .offerId(offerId)
            .passenger(BookRequest.PassengerDto.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phoneNumber("+18005559999")
                .countryCode("US")
                .build())
            .extraPassengers(java.util.List.of(
                BookRequest.ExtraPassengerDto.builder().firstName("Bob").lastName("Smith").build(),
                BookRequest.ExtraPassengerDto.builder().firstName("Charlie").lastName("Smith").build()
            ))
            .flight(BookRequest.FlightDto.builder()
                .airline("UA")
                .flightNumber("456")
                .build())
            .build();
    }
    
    // Invalid booking requests
    public static BookRequest missingPassengerFirstName(String offerId) {
        return BookRequest.builder()
            .offerId(offerId)
            .passenger(BookRequest.PassengerDto.builder()
                .lastName("Doe")
                .email("john@example.com")
                .phoneNumber("+18005551234")
                .countryCode("US")
                .build())
            .build();
    }
    
    public static BookRequest invalidEmail(String offerId) {
        return BookRequest.builder()
            .offerId(offerId)
            .passenger(BookRequest.PassengerDto.builder()
                .firstName("John")
                .lastName("Doe")
                .email("not-an-email")
                .phoneNumber("+18005551234")
                .countryCode("US")
                .build())
            .build();
    }
    
    // ===================
    // TOKEN PAYLOADS
    // ===================
    
    public static OfferPayload validOfferPayload() {
        return OfferPayload.of("MOZIO", "search-123", "result-456", 
            Instant.now().plus(20, ChronoUnit.MINUTES));
    }
    
    public static OfferPayload expiredOfferPayload() {
        return OfferPayload.of("MOZIO", "search-old", "result-old",
            Instant.now().minus(1, ChronoUnit.HOURS));
    }
    
    public static OfferPayload stubOfferPayload() {
        return OfferPayload.of("STUB", "stub-search-001", "stub-result-001",
            Instant.now().plus(60, ChronoUnit.MINUTES));
    }
    
    public static BookingPayload validBookingPayload() {
        return BookingPayload.of("MOZIO", "reservation-789", "MZ123456");
    }
    
    public static BookingPayload stubBookingPayload() {
        return BookingPayload.of("STUB", "stub-res-001", "STUB-CONF-001");
    }
}
