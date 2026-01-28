package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.adapters.idcodec.BookingIdCodec;
import com.arcube.transferaggregator.adapters.idcodec.BookingPayload;
import com.arcube.transferaggregator.adapters.idcodec.OfferIdCodec;
import com.arcube.transferaggregator.adapters.idcodec.OfferPayload;
import com.arcube.transferaggregator.config.AggregatorProperties;
import com.arcube.transferaggregator.domain.Money;
import com.arcube.transferaggregator.domain.Offer;
import com.arcube.transferaggregator.dto.BookingChangeCommitRequest;
import com.arcube.transferaggregator.dto.BookingChangeResponse;
import com.arcube.transferaggregator.dto.BookingChangeSearchRequest;
import com.arcube.transferaggregator.dto.SearchResponse;
import com.arcube.transferaggregator.ports.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TransferBookingChangeServiceTest {

    @Test
    void searchForChangeUsesReservationChangeSupplier() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        when(props.getGlobalTimeoutSeconds()).thenReturn(5);

        BookingPayload bookingPayload = BookingPayload.of("STUB", "res-1", "c1");
        when(bookingIdCodec.decode("booking-1")).thenReturn(bookingPayload);

        ChangeSupplier supplier = new ChangeSupplier("STUB", true);

        Offer offer = Offer.builder()
            .offerId("offer-1")
            .supplierCode("STUB")
            .expiresAt(Instant.now().plusSeconds(600))
            .build();

        supplier.searchResult = SupplierReservationChangeSearchResult.success("chg-1", List.of(offer), "res-1", "STUB");

        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));

        TransferBookingChangeService service = new TransferBookingChangeService(
            registry, bookingIdCodec, offerIdCodec, props);

        BookingChangeSearchRequest request = BookingChangeSearchRequest.builder()
            .bookingId("booking-1")
            .build();

        SearchResponse response = service.searchForChange(request);

        assertThat(response.getSearchId()).isEqualTo("chg-1");
        assertThat(response.getOffers()).hasSize(1);
    }

    @Test
    void searchForChangeThrowsWhenSupplierMissing() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);

        BookingPayload bookingPayload = BookingPayload.of("STUB", "res-1", "c1");
        when(bookingIdCodec.decode("booking-1")).thenReturn(bookingPayload);
        when(registry.getSupplier("STUB")).thenReturn(Optional.empty());

        TransferBookingChangeService service = new TransferBookingChangeService(
            registry, bookingIdCodec, offerIdCodec, props);

        BookingChangeSearchRequest request = BookingChangeSearchRequest.builder()
            .bookingId("booking-1")
            .build();

        assertThatThrownBy(() -> service.searchForChange(request))
            .isInstanceOf(com.arcube.transferaggregator.exception.SupplierNotFoundException.class);
    }

    @Test
    void searchForChangeFallsBackWhenSupplierIsNotChangeSupplier() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);

        BookingPayload bookingPayload = BookingPayload.of("STUB", "res-1", "c1");
        when(bookingIdCodec.decode("booking-1")).thenReturn(bookingPayload);

        BasicSupplier supplier = new BasicSupplier("STUB");
        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));

        TransferBookingChangeService service = new TransferBookingChangeService(
            registry, bookingIdCodec, offerIdCodec, props);

        BookingChangeSearchRequest request = BookingChangeSearchRequest.builder()
            .bookingId("booking-1")
            .build();

        SearchResponse response = service.searchForChange(request);

        assertThat(response.getSearchId()).startsWith("fallback-");
        assertThat(response.getOffers()).isEmpty();
        assertThat(response.isIncomplete()).isFalse();
    }

    @Test
    void searchForChangeFallsBackWhenSupplierDoesNotSupportChanges() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);

        BookingPayload bookingPayload = BookingPayload.of("STUB", "res-1", "c1");
        when(bookingIdCodec.decode("booking-1")).thenReturn(bookingPayload);

        ChangeSupplier supplier = new ChangeSupplier("STUB", false);
        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));

        TransferBookingChangeService service = new TransferBookingChangeService(
            registry, bookingIdCodec, offerIdCodec, props);

        BookingChangeSearchRequest request = BookingChangeSearchRequest.builder()
            .bookingId("booking-1")
            .build();

        SearchResponse response = service.searchForChange(request);

        assertThat(response.getSearchId()).startsWith("fallback-");
        assertThat(response.getOffers()).isEmpty();
        assertThat(response.isIncomplete()).isFalse();
    }

    @Test
    void searchForChangeHandlesErrorMessageAndMapsLocations() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        when(props.getGlobalTimeoutSeconds()).thenReturn(5);

        BookingPayload bookingPayload = BookingPayload.of("STUB", "res-1", "c1");
        when(bookingIdCodec.decode("booking-1")).thenReturn(bookingPayload);

        ChangeSupplier supplier = new ChangeSupplier("STUB", true);
        supplier.searchResult = SupplierReservationChangeSearchResult.error(
            "boom", "res-1", "STUB");

        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));

        TransferBookingChangeService service = new TransferBookingChangeService(
            registry, bookingIdCodec, offerIdCodec, props);

        BookingChangeSearchRequest request = BookingChangeSearchRequest.builder()
            .bookingId("booking-1")
            .newPickupLocation(BookingChangeSearchRequest.LocationDto.builder()
                .address("A").iataCode("AAA").placeId("p1").latitude(1.0).longitude(2.0).build())
            .newDropoffLocation(BookingChangeSearchRequest.LocationDto.builder()
                .address("B").iataCode("BBB").placeId("p2").latitude(3.0).longitude(4.0).build())
            .build();

        SearchResponse response = service.searchForChange(request);

        assertThat(response.getSearchId()).isNull();
        assertThat(response.getOffers()).isEmpty();
        assertThat(response.isIncomplete()).isFalse();
    }

    @Test
    void searchForChangeMapsIncludedAmenities() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        when(props.getGlobalTimeoutSeconds()).thenReturn(5);

        BookingPayload bookingPayload = BookingPayload.of("STUB", "res-1", "c1");
        when(bookingIdCodec.decode("booking-1")).thenReturn(bookingPayload);

        ChangeSupplier supplier = new ChangeSupplier("STUB", true);

        Offer offer = Offer.builder()
            .offerId("offer-1")
            .supplierCode("STUB")
            .includedAmenities(List.of(
                com.arcube.transferaggregator.domain.Amenity.included("wifi", "WiFi")))
            .expiresAt(Instant.now().plusSeconds(600))
            .build();

        supplier.searchResult = SupplierReservationChangeSearchResult.success(
            "chg-1", List.of(offer), "res-1", "STUB");

        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));

        TransferBookingChangeService service = new TransferBookingChangeService(
            registry, bookingIdCodec, offerIdCodec, props);

        BookingChangeSearchRequest request = BookingChangeSearchRequest.builder()
            .bookingId("booking-1")
            .build();

        SearchResponse response = service.searchForChange(request);

        assertThat(response.getOffers()).hasSize(1);
        assertThat(response.getOffers().get(0).getIncludedAmenities()).containsExactly("wifi");
    }

    @Test
    void searchForChangeMarksIncompleteWhenSupplierNotComplete() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        when(props.getGlobalTimeoutSeconds()).thenReturn(5);

        BookingPayload bookingPayload = BookingPayload.of("STUB", "res-1", "c1");
        when(bookingIdCodec.decode("booking-1")).thenReturn(bookingPayload);

        ChangeSupplier supplier = new ChangeSupplier("STUB", true);
        supplier.searchResult = SupplierReservationChangeSearchResult.builder()
            .searchId("chg-2")
            .offers(List.of())
            .complete(false)
            .oldReservationId("res-1")
            .supplierCode("STUB")
            .build();

        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));

        TransferBookingChangeService service = new TransferBookingChangeService(
            registry, bookingIdCodec, offerIdCodec, props);

        BookingChangeSearchRequest request = BookingChangeSearchRequest.builder()
            .bookingId("booking-1")
            .build();

        SearchResponse response = service.searchForChange(request);

        assertThat(response.isIncomplete()).isTrue();
    }

    @Test
    void commitChangeMapsSuccessResponse() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        when(props.getGlobalTimeoutSeconds()).thenReturn(5);

        BookingPayload oldPayload = BookingPayload.of("STUB", "res-1", "c1");
        when(bookingIdCodec.decode("booking-1")).thenReturn(oldPayload);

        OfferPayload newOffer = OfferPayload.of("STUB", "s1", "r2", Instant.now().plusSeconds(600));
        when(offerIdCodec.decode("result-1")).thenReturn(newOffer);

        ChangeSupplier supplier = new ChangeSupplier("STUB", true);
        supplier.changeResult = SupplierReservationChangeResult.success(
            "res-2", "CONF-2", "res-1", Money.of(120, "USD"), Money.of(10, "USD"));

        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));
        when(bookingIdCodec.encode(any(BookingPayload.class))).thenReturn("booking-2");

        TransferBookingChangeService service = new TransferBookingChangeService(
            registry, bookingIdCodec, offerIdCodec, props);

        BookingChangeCommitRequest request = BookingChangeCommitRequest.builder()
            .oldBookingId("booking-1")
            .searchId("s1")
            .resultId("result-1")
            .email("a@b.com")
            .phoneNumber("1")
            .countryCode("US")
            .build();

        BookingChangeResponse response = service.commitChange(request, "key");

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getNewBookingId()).isEqualTo("booking-2");
    }

    @Test
    void commitChangeThrowsWhenSupplierMissing() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);

        BookingPayload oldPayload = BookingPayload.of("STUB", "res-1", "c1");
        when(bookingIdCodec.decode("booking-1")).thenReturn(oldPayload);
        when(registry.getSupplier("STUB")).thenReturn(Optional.empty());

        TransferBookingChangeService service = new TransferBookingChangeService(
            registry, bookingIdCodec, offerIdCodec, props);

        BookingChangeCommitRequest request = BookingChangeCommitRequest.builder()
            .oldBookingId("booking-1")
            .searchId("s1")
            .resultId("result-1")
            .build();

        assertThatThrownBy(() -> service.commitChange(request, "key"))
            .isInstanceOf(com.arcube.transferaggregator.exception.SupplierNotFoundException.class);
    }

    @Test
    void commitChangeFallsBackWhenSupplierIsNotChangeSupplier() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);

        BookingPayload oldPayload = BookingPayload.of("STUB", "res-1", "c1");
        when(bookingIdCodec.decode("booking-1")).thenReturn(oldPayload);

        BasicSupplier supplier = new BasicSupplier("STUB");
        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));

        TransferBookingChangeService service = new TransferBookingChangeService(
            registry, bookingIdCodec, offerIdCodec, props);

        BookingChangeCommitRequest request = BookingChangeCommitRequest.builder()
            .oldBookingId("booking-1")
            .searchId("s1")
            .resultId("result-1")
            .build();

        BookingChangeResponse response = service.commitChange(request, "key");

        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getErrorCode()).isEqualTo("NOT_SUPPORTED");
    }

    @Test
    void commitChangeFallsBackWhenSupplierDoesNotSupportChanges() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);

        BookingPayload oldPayload = BookingPayload.of("STUB", "res-1", "c1");
        when(bookingIdCodec.decode("booking-1")).thenReturn(oldPayload);

        ChangeSupplier supplier = new ChangeSupplier("STUB", false);
        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));

        TransferBookingChangeService service = new TransferBookingChangeService(
            registry, bookingIdCodec, offerIdCodec, props);

        BookingChangeCommitRequest request = BookingChangeCommitRequest.builder()
            .oldBookingId("booking-1")
            .searchId("s1")
            .resultId("result-1")
            .build();

        BookingChangeResponse response = service.commitChange(request, "key");

        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getErrorCode()).isEqualTo("NOT_SUPPORTED");
    }

    @Test
    void commitChangeHandlesFailedStatus() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        when(props.getGlobalTimeoutSeconds()).thenReturn(5);

        BookingPayload oldPayload = BookingPayload.of("STUB", "res-1", "c1");
        when(bookingIdCodec.decode("booking-1")).thenReturn(oldPayload);

        OfferPayload newOffer = OfferPayload.of("STUB", "s1", "r2", Instant.now().plusSeconds(600));
        when(offerIdCodec.decode("result-1")).thenReturn(newOffer);

        ChangeSupplier supplier = new ChangeSupplier("STUB", true);
        supplier.changeResult = SupplierReservationChangeResult.failed("E1", "bad");

        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));

        TransferBookingChangeService service = new TransferBookingChangeService(
            registry, bookingIdCodec, offerIdCodec, props);

        BookingChangeCommitRequest request = BookingChangeCommitRequest.builder()
            .oldBookingId("booking-1")
            .searchId("s1")
            .resultId("result-1")
            .build();

        BookingChangeResponse response = service.commitChange(request, "key");

        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getErrorCode()).isEqualTo("E1");
    }

    @Test
    void commitChangeHandlesPendingStatus() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        when(props.getGlobalTimeoutSeconds()).thenReturn(5);

        BookingPayload oldPayload = BookingPayload.of("STUB", "res-1", "c1");
        when(bookingIdCodec.decode("booking-1")).thenReturn(oldPayload);

        OfferPayload newOffer = OfferPayload.of("STUB", "s1", "r2", Instant.now().plusSeconds(600));
        when(offerIdCodec.decode("result-1")).thenReturn(newOffer);

        ChangeSupplier supplier = new ChangeSupplier("STUB", true);
        supplier.changeResult = SupplierReservationChangeResult.pending("res-1");

        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));

        TransferBookingChangeService service = new TransferBookingChangeService(
            registry, bookingIdCodec, offerIdCodec, props);

        BookingChangeCommitRequest request = BookingChangeCommitRequest.builder()
            .oldBookingId("booking-1")
            .searchId("s1")
            .resultId("result-1")
            .build();

        BookingChangeResponse response = service.commitChange(request, "key");

        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getOldBookingId()).isEqualTo("booking-1");
    }

    private static final class BasicSupplier implements TransferSupplier {
        private final String code;

        private BasicSupplier(String code) {
            this.code = code;
        }

        @Override
        public String getSupplierCode() {
            return code;
        }

        @Override
        public String getSupplierName() {
            return code;
        }

        @Override
        public SupplierSearchResult search(com.arcube.transferaggregator.domain.SearchCommand command, Duration timeout) {
            return SupplierSearchResult.success(code, "s", List.of(), true, 1);
        }

        @Override
        public SupplierBookingResult book(com.arcube.transferaggregator.domain.BookCommand command, Duration timeout) {
            return SupplierBookingResult.pending(code, "res");
        }

        @Override
        public SupplierCancelResult cancel(com.arcube.transferaggregator.domain.CancelCommand command) {
            return SupplierCancelResult.success(code, command.reservationId(), Money.of(0, "USD"));
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }

    private static final class ChangeSupplier implements TransferSupplier, ReservationChangeSupplier {
        private final String code;
        private final boolean supportsChanges;
        private boolean enabled = true;
        private SupplierReservationChangeSearchResult searchResult =
            SupplierReservationChangeSearchResult.success("s", List.of(), "old", "STUB");
        private SupplierReservationChangeResult changeResult =
            SupplierReservationChangeResult.pending("old");

        private ChangeSupplier(String code, boolean supportsChanges) {
            this.code = code;
            this.supportsChanges = supportsChanges;
        }

        @Override
        public String getSupplierCode() {
            return code;
        }

        @Override
        public String getSupplierName() {
            return code;
        }

        @Override
        public SupplierSearchResult search(com.arcube.transferaggregator.domain.SearchCommand command, Duration timeout) {
            return SupplierSearchResult.success(code, "s", List.of(), true, 1);
        }

        @Override
        public SupplierBookingResult book(com.arcube.transferaggregator.domain.BookCommand command, Duration timeout) {
            return SupplierBookingResult.pending(code, "res");
        }

        @Override
        public SupplierCancelResult cancel(com.arcube.transferaggregator.domain.CancelCommand command) {
            return SupplierCancelResult.success(code, command.reservationId(), Money.of(0, "USD"));
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public boolean supportsReservationChanges() {
            return supportsChanges;
        }

        @Override
        public SupplierReservationChangeSearchResult searchForChange(
            com.arcube.transferaggregator.domain.ReservationChangeSearchCommand command, Duration timeout) {
            return searchResult;
        }

        @Override
        public SupplierReservationChangeResult changeReservation(
            com.arcube.transferaggregator.domain.ReservationChangeCommitCommand command, Duration timeout) {
            return changeResult;
        }
    }
}
