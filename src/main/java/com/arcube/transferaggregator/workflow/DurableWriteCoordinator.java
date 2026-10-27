package com.arcube.transferaggregator.workflow;

import com.arcube.transferaggregator.config.TenantContext;
import com.arcube.transferaggregator.domain.BookingStatus;
import com.arcube.transferaggregator.dto.BookRequest;
import com.arcube.transferaggregator.dto.BookResponse;
import com.arcube.transferaggregator.dto.BookingChangeCommitRequest;
import com.arcube.transferaggregator.dto.BookingChangeResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.function.Function;

/** Claims a write before contacting a supplier, then records its verified or ambiguous outcome. */
@Service
@Profile("!demo & !test")
public class DurableWriteCoordinator {
    private final WorkflowRepository repository;
    private final ObjectMapper mapper;

    public DurableWriteCoordinator(WorkflowRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public BookResponse book(BookRequest request, String clientKey, String supplier, Function<String, BookResponse> supplierCall) {
        String tenant = tenant();
        WorkflowRepository.Claim claim = repository.claim(tenant, "BOOKING", clientKey, fingerprint(request), supplier);
        String operationId = claim.operation().id().toString();
        if (!claim.acquired()) {
            String stored = repository.responseJson(claim.operation());
            return stored == null ? BookResponse.unknownOutcome(operationId) : read(stored, BookResponse.class);
        }
        BookResponse response;
        try {
            response = supplierCall.apply(operationId);
        } catch (Exception exception) {
            BookResponse unknown = BookResponse.unknownOutcome(operationId);
            repository.complete(claim.operation().id(), tenant, "UNKNOWN_OUTCOME", write(unknown));
            return unknown;
        }
        response.setOperationId(operationId);
        String state = switch (response.getStatus()) {
            case CONFIRMED -> "CONFIRMED";
            case FAILED, PRICE_CHANGED -> "FAILED";
            default -> "UNKNOWN_OUTCOME";
        };
        repository.complete(claim.operation().id(), tenant, state, write(response));
        return response;
    }

    public BookingChangeResponse change(BookingChangeCommitRequest request, String clientKey, String supplier,
                                        Function<String, BookingChangeResponse> supplierCall) {
        String tenant = tenant();
        WorkflowRepository.Claim claim = repository.claim(tenant, "CHANGE", clientKey, fingerprint(request), supplier);
        String operationId = claim.operation().id().toString();
        if (!claim.acquired()) {
            String stored = repository.responseJson(claim.operation());
            return stored == null ? BookingChangeResponse.unknownOutcome(operationId)
                : read(stored, BookingChangeResponse.class);
        }
        BookingChangeResponse response;
        try {
            response = supplierCall.apply(operationId);
        } catch (Exception exception) {
            BookingChangeResponse unknown = BookingChangeResponse.unknownOutcome(operationId);
            repository.complete(claim.operation().id(), tenant, "UNKNOWN_OUTCOME", write(unknown));
            return unknown;
        }
        response.setOperationId(operationId);
        String state = switch (response.getStatus()) {
            case "SUCCESS" -> "CONFIRMED";
            case "FAILED" -> "FAILED";
            default -> "UNKNOWN_OUTCOME";
        };
        repository.complete(claim.operation().id(), tenant, state, write(response));
        return response;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 120000)
    public void markStaleWrites() {
        repository.markStaleWrites();
    }

    @Scheduled(cron = "0 17 3 * * *")
    public void cleanExpiredRecords() {
        repository.cleanup();
    }

    private String tenant() {
        String tenant = TenantContext.getTenantId();
        if (tenant == null) throw new IllegalStateException("Authenticated tenant context is required");
        return tenant;
    }

    private String fingerprint(Object request) {
        return WorkflowRepository.sha256(write(request));
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize workflow payload", exception);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize stored workflow response", exception);
        }
    }
}
