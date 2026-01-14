package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.adapters.idcodec.BookingIdCodec;
import com.arcube.transferaggregator.adapters.idcodec.BookingPayload;
import com.arcube.transferaggregator.adapters.idcodec.OfferIdCodec;
import com.arcube.transferaggregator.adapters.idcodec.OfferPayload;
import com.arcube.transferaggregator.config.AggregatorProperties;
import com.arcube.transferaggregator.domain.*;
import com.arcube.transferaggregator.dto.*;
import com.arcube.transferaggregator.dto.SearchResponse.OfferDto;
import com.arcube.transferaggregator.exception.SupplierNotFoundException;
import com.arcube.transferaggregator.ports.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferBookingChangeService {

    private final SupplierRegistry supplierRegistry;
    private final BookingIdCodec bookingIdCodec;
    private final OfferIdCodec offerIdCodec;
    private final AggregatorProperties properties;

    /**
     * Step 1: Search for change options using supplier-native change search.
     * For suppliers supporting it (like Mozio), this uses the optimized
     * /v2/search/reservation_changes/ endpoint.
     */
    public SearchResponse searchForChange(BookingChangeSearchRequest request) {
        // Decode original booking to get supplier info
        BookingPayload bookingPayload = bookingIdCodec.decode(request.getBookingId());
        String supplierCode = bookingPayload.supplierCode();
        log.info("Searching changes for booking: supplier={}, reservationId={}", 
            supplierCode, bookingPayload.reservationId());

        TransferSupplier supplier = supplierRegistry.getSupplier(supplierCode)
            .orElseThrow(() -> new SupplierNotFoundException(supplierCode));

        // Check if supplier supports native reservation changes
        if (supplier instanceof ReservationChangeSupplier changeSupplier 
                && changeSupplier.supportsReservationChanges()) {
            return searchViaSupplierChangeEndpoint(request, bookingPayload, changeSupplier);
        }

        // Fallback: do a normal search (cancel + re-book will be needed)
        log.info("Supplier {} doesn't support native reservation changes, falling back to regular search", 
            supplierCode);
        return searchFallback(request, supplierCode);
    }

    /**
     * Step 2: Commit the reservation change.
     * For suppliers supporting it, uses atomic change operation.
     */
    public BookingChangeResponse commitChange(BookingChangeCommitRequest request, String idempotencyKey) {
        BookingPayload oldBookingPayload = bookingIdCodec.decode(request.getOldBookingId());
        String supplierCode = oldBookingPayload.supplierCode();
        
        log.info("Committing change: oldReservation={}, supplier={}", 
            oldBookingPayload.reservationId(), supplierCode);

        TransferSupplier supplier = supplierRegistry.getSupplier(supplierCode)
            .orElseThrow(() -> new SupplierNotFoundException(supplierCode));

        // Check if supplier supports native reservation changes
        if (supplier instanceof ReservationChangeSupplier changeSupplier 
                && changeSupplier.supportsReservationChanges()) {
            return commitViaSupplierChangeEndpoint(request, oldBookingPayload, changeSupplier);
        }

        // Fallback: not supported - requires manual cancel + re-book
        return BookingChangeResponse.failed("NOT_SUPPORTED", 
            "Supplier " + supplierCode + " doesn't support atomic reservation changes. " +
            "Please cancel the existing booking and create a new one.");
    }

    private SearchResponse searchViaSupplierChangeEndpoint(
            BookingChangeSearchRequest request,
            BookingPayload bookingPayload,
            ReservationChangeSupplier supplier) {
        
        ReservationChangeSearchCommand command = ReservationChangeSearchCommand.builder()
            .reservationId(bookingPayload.reservationId())
            .supplierCode(bookingPayload.supplierCode())
            .newPickupLocation(mapLocation(request.getNewPickupLocation()))
            .newDropoffLocation(mapLocation(request.getNewDropoffLocation()))
            .newPickupDateTime(request.getNewPickupDateTime())
            .newNumPassengers(request.getNewNumPassengers())
            .currency("USD")
            .build();

        Duration timeout = Duration.ofSeconds(properties.getGlobalTimeoutSeconds());
        SupplierReservationChangeSearchResult result = supplier.searchForChange(command, timeout);

        if (result.errorMessage() != null) {
            log.error("Reservation change search failed: {}", result.errorMessage());
            return SearchResponse.builder()
                .searchId(result.searchId())
                .offers(List.of())
                .incomplete(false)
                .build();
        }

        List<OfferDto> offerDtos = result.offers().stream()
            .map(this::mapToOfferDto)
            .toList();

        log.info("Found {} change options for reservation {}", 
            offerDtos.size(), bookingPayload.reservationId());

        return SearchResponse.builder()
            .searchId(result.searchId())
            .offers(offerDtos)
            .incomplete(!result.complete())
            .build();
    }

    private BookingChangeResponse commitViaSupplierChangeEndpoint(
            BookingChangeCommitRequest request,
            BookingPayload oldBookingPayload,
            ReservationChangeSupplier supplier) {

        OfferPayload newOfferPayload = offerIdCodec.decode(request.getResultId());

        ReservationChangeCommitCommand command = ReservationChangeCommitCommand.builder()
            .oldReservationId(oldBookingPayload.reservationId())
            .searchId(request.getSearchId())
            .resultId(newOfferPayload.resultId())
            .email(request.getEmail())
            .phoneNumber(request.getPhoneNumber())
            .countryCode(request.getCountryCode())
            .specialInstructions(request.getSpecialInstructions())
            .useExistingPayment(request.isUseExistingPayment())
            .build();

        Duration timeout = Duration.ofSeconds(properties.getGlobalTimeoutSeconds());
        SupplierReservationChangeResult result = supplier.changeReservation(command, timeout);

        if (result.status() == BookingStatus.FAILED) {
            return BookingChangeResponse.failed(result.errorCode(), result.errorMessage());
        }

        if (result.status() == BookingStatus.PENDING) {
            return BookingChangeResponse.pending(request.getOldBookingId());
        }

        // Encode new booking ID
        String newBookingId = bookingIdCodec.encode(
            BookingPayload.of(oldBookingPayload.supplierCode(), 
                result.newReservationId(), result.newConfirmationNumber()));

        return BookingChangeResponse.success(
            newBookingId, result.newConfirmationNumber(), request.getOldBookingId());
    }

    private SearchResponse searchFallback(BookingChangeSearchRequest request, String originalSupplierCode) {
        // For non-supporting suppliers, return empty with instruction
        log.warn("Supplier {} doesn't support reservation changes, user should cancel and re-book", 
            originalSupplierCode);
        return SearchResponse.builder()
            .searchId("fallback-" + System.currentTimeMillis())
            .offers(List.of())
            .incomplete(false)
            .build();
    }

    private Location mapLocation(BookingChangeSearchRequest.LocationDto loc) {
        if (loc == null) return null;
        return new Location(loc.getAddress(), loc.getIataCode(), loc.getPlaceId(),
            loc.getLatitude(), loc.getLongitude(), null, null);
    }

    private OfferDto mapToOfferDto(Offer o) {
        return OfferDto.builder()
            .offerId(o.offerId())
            .supplierCode(o.supplierCode())
            .vehicle(o.vehicle())
            .provider(o.provider())
            .totalPrice(o.totalPrice())
            .cancellation(o.cancellation())
            .estimatedDurationMinutes(o.estimatedDurationMinutes())
            .flightInfoRequired(o.flightInfoRequired())
            .extraPassengerInfoRequired(o.extraPassengerInfoRequired())
            .expiresAt(o.expiresAt())
            .includedAmenities(o.includedAmenities() != null ? 
                o.includedAmenities().stream().map(Amenity::key).toList() : List.of())
            .extras(o.extras())
            .build();
    }
}
