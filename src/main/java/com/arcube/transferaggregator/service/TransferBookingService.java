package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.adapters.idcodec.*;
import com.arcube.transferaggregator.config.AggregatorProperties;
import com.arcube.transferaggregator.config.TenantContext;
import com.arcube.transferaggregator.domain.BookCommand;
import com.arcube.transferaggregator.domain.BookingStatus;
import com.arcube.transferaggregator.dto.BookRequest;
import com.arcube.transferaggregator.dto.BookResponse;
import com.arcube.transferaggregator.exception.SupplierNotFoundException;
import com.arcube.transferaggregator.ports.SupplierBookingResult;
import com.arcube.transferaggregator.ports.SupplierRegistry;
import com.arcube.transferaggregator.ports.TransferSupplier;
import com.arcube.transferaggregator.workflow.DurableWriteCoordinator;
import com.arcube.transferaggregator.resilience.SupplierOperationPolicy;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Orchestrates booking operations */
@Slf4j
@Service
public class TransferBookingService {
    
    private final SupplierRegistry supplierRegistry;
    private final OfferIdCodec offerIdCodec;
    private final BookingIdCodec bookingIdCodec;
    private final AggregatorProperties properties;
    private final Cache<String, BookResponse> idempotencyCache;
    private final DurableWriteCoordinator durableWrites;

    @Autowired
    public TransferBookingService(SupplierRegistry supplierRegistry, OfferIdCodec offerIdCodec,
                                  BookingIdCodec bookingIdCodec, AggregatorProperties properties,
                                  Cache<String, BookResponse> idempotencyCache,
                                  Optional<DurableWriteCoordinator> durableWrites) {
        this.supplierRegistry = supplierRegistry;
        this.offerIdCodec = offerIdCodec;
        this.bookingIdCodec = bookingIdCodec;
        this.properties = properties;
        this.idempotencyCache = idempotencyCache;
        this.durableWrites = durableWrites.orElse(null);
    }

    TransferBookingService(SupplierRegistry supplierRegistry, OfferIdCodec offerIdCodec,
                           BookingIdCodec bookingIdCodec, AggregatorProperties properties,
                           Cache<String, BookResponse> idempotencyCache) {
        this(supplierRegistry, offerIdCodec, bookingIdCodec, properties, idempotencyCache,
            Optional.empty());
    }
    
    public BookResponse book(BookRequest request, String idempotencyKey) {
        OfferPayload offerPayload = offerIdCodec.decode(request.getOfferId());
        if (durableWrites != null) {
            return durableWrites.book(request, idempotencyKey, offerPayload.supplierCode(),
                operationId -> invokeSupplier(request, offerPayload, operationId));
        }
        String tenantId = TenantContext.getTenantIdOrDefault("default");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = generateIdempotencyKey(request);
        }
        String scopedCacheKey = tenantId + ":" + idempotencyKey;
        
        BookResponse cached = idempotencyCache.getIfPresent(scopedCacheKey);
        if (cached != null) {
            log.info("Returning cached response for idempotency key");
            return cached;
        }
        
        BookResponse response = invokeSupplier(request, offerPayload, idempotencyKey);
        
        if (response.getStatus() != BookingStatus.PENDING) {
            idempotencyCache.put(scopedCacheKey, response);
        }
        
        log.info("Booking result: status={}", response.getStatus());
        return response;
    }

    private BookResponse invokeSupplier(BookRequest request, OfferPayload offerPayload, String trackingId) {
        TransferSupplier supplier = supplierRegistry.getSupplier(offerPayload.supplierCode())
            .orElseThrow(() -> new SupplierNotFoundException(offerPayload.supplierCode()));
        BookCommand command = mapToCommand(request, offerPayload, trackingId);
        SupplierBookingResult result = supplier.book(command, SupplierOperationPolicy.timeout(properties,
            offerPayload.supplierCode(), SupplierOperationPolicy.Operation.BOOKING,
            Duration.ofSeconds(properties.getGlobalTimeoutSeconds())));
        return mapToResponse(result, offerPayload.supplierCode());
    }
    
    private BookCommand mapToCommand(BookRequest req, OfferPayload offer, String trackingId) {
        return BookCommand.builder()
            .searchId(offer.searchId())
            .resultId(offer.resultId())
            .passenger(new BookCommand.PassengerInfo(
                req.getPassenger().getFirstName(), req.getPassenger().getLastName(),
                req.getPassenger().getEmail(), req.getPassenger().getPhoneNumber(),
                req.getPassenger().getCountryCode()))
            .flight(req.getFlight() != null ? new BookCommand.FlightInfo(
                req.getFlight().getAirline(), req.getFlight().getFlightNumber()) : null)
            .extraPassengers(req.getExtraPassengers() != null
                ? req.getExtraPassengers().stream()
                    .map(ep -> new BookCommand.ExtraPassenger(ep.getFirstName(), ep.getLastName()))
                    .collect(Collectors.toList())
                : List.of())
            .specialInstructions(req.getSpecialInstructions())
            .partnerTrackingId(trackingId)
            .build();
    }
    
    private BookResponse mapToResponse(SupplierBookingResult result, String supplierCode) {
        if (result.status() == BookingStatus.PRICE_CHANGED) return BookResponse.priceChanged();
        if (result.status() == BookingStatus.FAILED) return BookResponse.failed(result.errorCode(), result.errorMessage(), "RETRY");
        
        String bookingId = null;
        if (result.reservationId() != null) {
            bookingId = bookingIdCodec.encode(BookingPayload.of(supplierCode, result.reservationId(), result.confirmationNumber()));
        }
        
        if (result.status() == BookingStatus.PENDING) return BookResponse.pending(bookingId);
        
        return BookResponse.confirmed(bookingId, result.confirmationNumber(), result.totalPrice(),
            result.pickupInstructions(), BookResponse.ProviderContactDto.builder().name(supplierCode).build());
    }
    
    private String generateIdempotencyKey(BookRequest request) {
        try {
            String data = request.getOfferId() + "|" + request.getPassenger().getEmail();
            byte[] hash = createMessageDigest().digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 32);
        } catch (Exception e) {
            return java.util.UUID.randomUUID().toString();
        }
    }

    protected MessageDigest createMessageDigest() throws Exception {
        return MessageDigest.getInstance("SHA-256");
    }
}
