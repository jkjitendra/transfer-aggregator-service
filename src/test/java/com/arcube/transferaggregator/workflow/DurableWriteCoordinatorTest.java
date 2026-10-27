package com.arcube.transferaggregator.workflow;

import com.arcube.transferaggregator.config.TenantContext;
import com.arcube.transferaggregator.domain.BookingStatus;
import com.arcube.transferaggregator.dto.BookRequest;
import com.arcube.transferaggregator.dto.BookResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DurableWriteCoordinatorTest {
    @Test
    void uncertainSupplierWriteIsRecordedWithoutAutomaticRetry() {
        WorkflowRepository repository = mock(WorkflowRepository.class);
        UUID id = UUID.randomUUID();
        var operation = new WorkflowRepository.Operation(id, "tenant-a", "BOOKING", "IN_PROGRESS",
            "STUB", WorkflowRepository.sha256("request"), null, Instant.now(), Instant.now());
        when(repository.claim(eq("tenant-a"), eq("BOOKING"), eq("client-key"), anyString(), eq("STUB")))
            .thenReturn(new WorkflowRepository.Claim(operation, true));
        DurableWriteCoordinator coordinator = new DurableWriteCoordinator(repository, new ObjectMapper());
        AtomicInteger calls = new AtomicInteger();
        TenantContext.setTenantId("tenant-a");
        try {
            BookResponse response = coordinator.book(BookRequest.builder().offerId("offer").build(),
                "client-key", "STUB", ignored -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("connection reset after write");
                });
            assertThat(response.getStatus()).isEqualTo(BookingStatus.UNKNOWN_OUTCOME);
            assertThat(response.getOperationId()).isEqualTo(id.toString());
            assertThat(calls).hasValue(1);
            verify(repository).complete(eq(id), eq("tenant-a"), eq("UNKNOWN_OUTCOME"), anyString());
        } finally {
            TenantContext.clear();
        }
    }
}
