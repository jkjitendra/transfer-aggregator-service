package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.adapters.idcodec.BookingIdCodec;
import com.arcube.transferaggregator.adapters.idcodec.BookingPayload;
import com.arcube.transferaggregator.adapters.idcodec.OfferIdCodec;
import com.arcube.transferaggregator.adapters.idcodec.OfferPayload;
import com.arcube.transferaggregator.config.AggregatorProperties;
import com.arcube.transferaggregator.domain.BookingStatus;
import com.arcube.transferaggregator.domain.Money;
import com.arcube.transferaggregator.dto.BookRequest;
import com.arcube.transferaggregator.dto.BookResponse;
import com.arcube.transferaggregator.ports.SupplierBookingResult;
import com.arcube.transferaggregator.ports.SupplierRegistry;
import com.arcube.transferaggregator.ports.TransferSupplier;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TransferBookingServiceTest {

    @Test
    void returnsCachedResponseForIdempotencyKey() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        Cache<String, BookResponse> cache = Caffeine.newBuilder().build();

        BookResponse cached = BookResponse.pending("b1");
        cache.put("key-1", cached);

        TransferBookingService service = new TransferBookingService(
            registry, offerIdCodec, bookingIdCodec, props, cache);

        BookRequest request = BookRequest.builder()
            .offerId("offer")
            .passenger(BookRequest.PassengerDto.builder()
                .firstName("A")
                .lastName("B")
                .email("a@b.com")
                .phoneNumber("1")
                .countryCode("US")
                .build())
            .build();

        BookResponse response = service.book(request, "key-1");

        assertThat(response).isSameAs(cached);
        verifyNoInteractions(registry, offerIdCodec, bookingIdCodec);
    }

    @Test
    void booksAndMapsConfirmedResponse() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        Cache<String, BookResponse> cache = Caffeine.newBuilder().build();

        OfferPayload payload = OfferPayload.of("STUB", "search-1", "result-1", Instant.now().plusSeconds(600));
        when(offerIdCodec.decode("offer")).thenReturn(payload);
        when(props.getGlobalTimeoutSeconds()).thenReturn(5);

        TransferSupplier supplier = mock(TransferSupplier.class);
        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));
        when(bookingIdCodec.encode(any(BookingPayload.class))).thenReturn("booking-123");

        SupplierBookingResult supplierResult = SupplierBookingResult.confirmed(
            "STUB", "res-1", "CONF-1", Money.of(55.0, "USD"), "pickup");
        when(supplier.book(any(), any(Duration.class))).thenReturn(supplierResult);

        TransferBookingService service = new TransferBookingService(
            registry, offerIdCodec, bookingIdCodec, props, cache);

        BookRequest request = BookRequest.builder()
            .offerId("offer")
            .passenger(BookRequest.PassengerDto.builder()
                .firstName("A")
                .lastName("B")
                .email("a@b.com")
                .phoneNumber("1")
                .countryCode("US")
                .build())
            .build();

        BookResponse response = service.book(request, "key-2");

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.getBookingId()).isEqualTo("booking-123");

        ArgumentCaptor<com.arcube.transferaggregator.domain.BookCommand> captor =
            ArgumentCaptor.forClass(com.arcube.transferaggregator.domain.BookCommand.class);
        verify(supplier).book(captor.capture(), any(Duration.class));
        assertThat(captor.getValue().searchId()).isEqualTo("search-1");
        assertThat(captor.getValue().resultId()).isEqualTo("result-1");
    }

    @Test
    void mapsPriceChangedAndFailedStatuses() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        Cache<String, BookResponse> cache = Caffeine.newBuilder().build();

        OfferPayload payload = OfferPayload.of("STUB", "search-1", "result-1", Instant.now().plusSeconds(600));
        when(offerIdCodec.decode("offer")).thenReturn(payload);
        when(props.getGlobalTimeoutSeconds()).thenReturn(5);

        TransferSupplier supplier = mock(TransferSupplier.class);
        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));
        when(supplier.book(any(), any(Duration.class)))
            .thenReturn(SupplierBookingResult.priceChanged("STUB"))
            .thenReturn(SupplierBookingResult.failed("STUB", "ERR", "Bad"));

        TransferBookingService service = new TransferBookingService(
            registry, offerIdCodec, bookingIdCodec, props, cache);

        BookRequest request = BookRequest.builder()
            .offerId("offer")
            .passenger(BookRequest.PassengerDto.builder()
                .firstName("A")
                .lastName("B")
                .email("a@b.com")
                .phoneNumber("1")
                .countryCode("US")
                .build())
            .build();

        BookResponse priceChanged = service.book(request, "k1");
        assertThat(priceChanged.getStatus()).isEqualTo(BookingStatus.PRICE_CHANGED);

        BookResponse failed = service.book(request, "k2");
        assertThat(failed.getStatus()).isEqualTo(BookingStatus.FAILED);
        assertThat(failed.getErrorCode()).isEqualTo("ERR");
    }

    @Test
    void pendingDoesNotCacheAndBlankIdempotencyGeneratesKey() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        Cache<String, BookResponse> cache = Caffeine.newBuilder().build();

        OfferPayload payload = OfferPayload.of("STUB", "search-1", "result-1", Instant.now().plusSeconds(600));
        when(offerIdCodec.decode("offer")).thenReturn(payload);
        when(props.getGlobalTimeoutSeconds()).thenReturn(5);

        TransferSupplier supplier = mock(TransferSupplier.class);
        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));
        when(supplier.book(any(), any(Duration.class)))
            .thenReturn(SupplierBookingResult.pending("STUB", "res-1"));

        TransferBookingService service = new TransferBookingService(
            registry, offerIdCodec, bookingIdCodec, props, cache);

        BookRequest request = BookRequest.builder()
            .offerId("offer")
            .passenger(BookRequest.PassengerDto.builder()
                .firstName("A")
                .lastName("B")
                .email("a@b.com")
                .phoneNumber("1")
                .countryCode("US")
                .build())
            .build();

        BookResponse response = service.book(request, " ");
        assertThat(response.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(cache.estimatedSize()).isZero();
    }

    @Test
    void generatesIdempotencyKeyWhenNullAndMapsOptionalFields() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        Cache<String, BookResponse> cache = Caffeine.newBuilder().build();

        OfferPayload payload = OfferPayload.of("STUB", "search-1", "result-1", Instant.now().plusSeconds(600));
        when(offerIdCodec.decode("offer")).thenReturn(payload);
        when(props.getGlobalTimeoutSeconds()).thenReturn(5);

        TransferSupplier supplier = mock(TransferSupplier.class);
        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));
        when(bookingIdCodec.encode(any(BookingPayload.class))).thenReturn("booking-123");

        SupplierBookingResult supplierResult = SupplierBookingResult.confirmed(
            "STUB", "res-1", "CONF-1", Money.of(55.0, "USD"), "pickup");
        when(supplier.book(any(), any(Duration.class))).thenReturn(supplierResult);

        TransferBookingService service = new TransferBookingService(
            registry, offerIdCodec, bookingIdCodec, props, cache);

        BookRequest request = BookRequest.builder()
            .offerId("offer")
            .passenger(BookRequest.PassengerDto.builder()
                .firstName("A")
                .lastName("B")
                .email("a@b.com")
                .phoneNumber("1")
                .countryCode("US")
                .build())
            .flight(BookRequest.FlightDto.builder().airline("AA").flightNumber("1").build())
            .extraPassengers(Optional.of(BookRequest.ExtraPassengerDto.builder()
                    .firstName("E").lastName("P").build())
                .stream().toList())
            .build();

        BookResponse response = service.book(request, null);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(cache.estimatedSize()).isEqualTo(1);

        ArgumentCaptor<com.arcube.transferaggregator.domain.BookCommand> captor =
            ArgumentCaptor.forClass(com.arcube.transferaggregator.domain.BookCommand.class);
        verify(supplier).book(captor.capture(), any(Duration.class));
        assertThat(captor.getValue().flight()).isNotNull();
        assertThat(captor.getValue().extraPassengers()).hasSize(1);
    }

    @Test
    void mapsConfirmedResponseWithoutReservationId() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        Cache<String, BookResponse> cache = Caffeine.newBuilder().build();

        OfferPayload payload = OfferPayload.of("STUB", "search-1", "result-1", Instant.now().plusSeconds(600));
        when(offerIdCodec.decode("offer")).thenReturn(payload);
        when(props.getGlobalTimeoutSeconds()).thenReturn(5);

        TransferSupplier supplier = mock(TransferSupplier.class);
        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));

        SupplierBookingResult supplierResult = new SupplierBookingResult(
            "STUB", null, "CONF-1", BookingStatus.CONFIRMED, Money.of(55.0, "USD"), "pickup", null, null);
        when(supplier.book(any(), any(Duration.class))).thenReturn(supplierResult);

        TransferBookingService service = new TransferBookingService(
            registry, offerIdCodec, bookingIdCodec, props, cache);

        BookRequest request = BookRequest.builder()
            .offerId("offer")
            .passenger(BookRequest.PassengerDto.builder()
                .firstName("A")
                .lastName("B")
                .email("a@b.com")
                .phoneNumber("1")
                .countryCode("US")
                .build())
            .build();

        BookResponse response = service.book(request, "k1");

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.getBookingId()).isNull();
    }

    @Test
    void generateIdempotencyKeyHandlesDigestFailure() throws Exception {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        OfferIdCodec offerIdCodec = mock(OfferIdCodec.class);
        BookingIdCodec bookingIdCodec = mock(BookingIdCodec.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        Cache<String, BookResponse> cache = Caffeine.newBuilder().build();

        OfferPayload payload = OfferPayload.of("STUB", "search-1", "result-1", Instant.now().plusSeconds(600));
        when(offerIdCodec.decode("offer")).thenReturn(payload);
        when(props.getGlobalTimeoutSeconds()).thenReturn(5);

        TransferSupplier supplier = mock(TransferSupplier.class);
        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));
        when(supplier.book(any(), any(Duration.class)))
            .thenReturn(SupplierBookingResult.pending("STUB", "res-1"));

        TransferBookingService service = new TransferBookingService(
            registry, offerIdCodec, bookingIdCodec, props, cache) {
            @Override
            protected MessageDigest createMessageDigest() throws Exception {
                throw new Exception("boom");
            }
        };

        BookRequest request = BookRequest.builder()
            .offerId("offer")
            .passenger(BookRequest.PassengerDto.builder()
                .firstName("A")
                .lastName("B")
                .email("a@b.com")
                .phoneNumber("1")
                .countryCode("US")
                .build())
            .build();

        BookResponse response = service.book(request, null);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.PENDING);
    }
}
