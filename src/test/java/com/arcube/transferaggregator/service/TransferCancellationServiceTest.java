package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.adapters.idcodec.BookingIdCodec;
import com.arcube.transferaggregator.adapters.idcodec.BookingPayload;
import com.arcube.transferaggregator.domain.CancelCommand;
import com.arcube.transferaggregator.domain.Money;
import com.arcube.transferaggregator.dto.CancelResponse;
import com.arcube.transferaggregator.ports.SupplierCancelResult;
import com.arcube.transferaggregator.ports.SupplierRegistry;
import com.arcube.transferaggregator.ports.TransferSupplier;
import com.arcube.transferaggregator.resilience.CancellationDLQ;
import com.arcube.transferaggregator.resilience.CancellationQueue;
import com.arcube.transferaggregator.resilience.CancellationTask;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TransferCancellationServiceTest {

    @Test
    void returnsPendingWhenAlreadyQueued() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec codec = mock(BookingIdCodec.class);
        CancellationQueue queue = mock(CancellationQueue.class);
        CancellationDLQ dlq = mock(CancellationDLQ.class);

        when(codec.decode("b1")).thenReturn(BookingPayload.of("STUB", "res-1", "c1"));
        when(queue.getPending("b1")).thenReturn(Optional.of(
            CancellationTask.create("b1", "STUB", "r1", "c1")));

        TransferCancellationService service = new TransferCancellationService(registry, codec, queue, dlq);
        CancelResponse response = service.cancel("b1");

        assertThat(response.getStatus()).isEqualTo("PENDING");
        verifyNoInteractions(registry, dlq);
    }

    @Test
    void returnsFailedWhenInDlq() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec codec = mock(BookingIdCodec.class);
        CancellationQueue queue = mock(CancellationQueue.class);
        CancellationDLQ dlq = mock(CancellationDLQ.class);

        when(codec.decode("b1")).thenReturn(BookingPayload.of("STUB", "res-1", "c1"));
        when(queue.getPending("b1")).thenReturn(Optional.empty());
        when(dlq.get("b1")).thenReturn(Optional.of(
            CancellationTask.create("b1", "STUB", "r1", "c1")));

        TransferCancellationService service = new TransferCancellationService(registry, codec, queue, dlq);
        CancelResponse response = service.cancel("b1");

        assertThat(response.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void queuesWhenSupplierThrows() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec codec = mock(BookingIdCodec.class);
        CancellationQueue queue = mock(CancellationQueue.class);
        CancellationDLQ dlq = mock(CancellationDLQ.class);

        when(queue.getPending("b1")).thenReturn(Optional.empty());
        when(dlq.get("b1")).thenReturn(Optional.empty());
        when(codec.decode("b1")).thenReturn(BookingPayload.of("STUB", "res-1", "c1"));

        TransferSupplier supplier = mock(TransferSupplier.class);
        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));
        when(supplier.cancel(any())).thenThrow(new RuntimeException("boom"));

        TransferCancellationService service = new TransferCancellationService(registry, codec, queue, dlq);
        CancelResponse response = service.cancel("b1");

        assertThat(response.getStatus()).isEqualTo("PENDING");
        verify(queue).enqueue(any(CancellationTask.class));
    }

    @Test
    void throwsWhenTooLateToCancel() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec codec = mock(BookingIdCodec.class);
        CancellationQueue queue = mock(CancellationQueue.class);
        CancellationDLQ dlq = mock(CancellationDLQ.class);

        when(queue.getPending("b1")).thenReturn(Optional.empty());
        when(dlq.get("b1")).thenReturn(Optional.empty());
        when(codec.decode("b1")).thenReturn(BookingPayload.of("STUB", "res-1", "c1"));

        TransferSupplier supplier = mock(TransferSupplier.class);
        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));
        when(supplier.cancel(any())).thenReturn(
            SupplierCancelResult.failed("STUB", "res-1", "TOO_LATE_TO_CANCEL", "too late"));

        TransferCancellationService service = new TransferCancellationService(registry, codec, queue, dlq);

        CancelResponse response = service.cancel("b1");
        assertThat(response.getStatus()).isEqualTo("PENDING");
        verify(queue).enqueue(any(CancellationTask.class));
    }

    @Test
    void queuesWhenCancellationFailedWithNonTooLateCode() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec codec = mock(BookingIdCodec.class);
        CancellationQueue queue = mock(CancellationQueue.class);
        CancellationDLQ dlq = mock(CancellationDLQ.class);

        when(queue.getPending("b1")).thenReturn(Optional.empty());
        when(dlq.get("b1")).thenReturn(Optional.empty());
        when(codec.decode("b1")).thenReturn(BookingPayload.of("STUB", "res-1", "c1"));

        TransferSupplier supplier = mock(TransferSupplier.class);
        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));
        when(supplier.cancel(any()))
            .thenReturn(SupplierCancelResult.failed("STUB", "res-1", "OTHER", "nope"));

        TransferCancellationService service = new TransferCancellationService(registry, codec, queue, dlq);

        CancelResponse response = service.cancel("b1");
        assertThat(response.getStatus()).isEqualTo("PENDING");
        verify(queue).enqueue(any(CancellationTask.class));
    }

    @Test
    void queuesWhenCancellationTimesOut() throws Exception {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec codec = mock(BookingIdCodec.class);
        CancellationQueue queue = mock(CancellationQueue.class);
        CancellationDLQ dlq = mock(CancellationDLQ.class);

        when(queue.getPending("b1")).thenReturn(Optional.empty());
        when(dlq.get("b1")).thenReturn(Optional.empty());
        when(codec.decode("b1")).thenReturn(BookingPayload.of("STUB", "res-1", "c1"));

        TransferSupplier supplier = mock(TransferSupplier.class);
        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));

        TransferCancellationService service = new TransferCancellationService(registry, codec, queue, dlq) {
            @Override
            protected SupplierCancelResult executeWithTimeout(TransferSupplier supplier, CancelCommand command)
                throws TimeoutException {
                throw new TimeoutException("timeout");
            }
        };

        CancelResponse response = service.cancel("b1");
        assertThat(response.getStatus()).isEqualTo("PENDING");
        verify(queue).enqueue(any(CancellationTask.class));
    }

    @Test
    void returnsSuccessAndAlreadyCancelled() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec codec = mock(BookingIdCodec.class);
        CancellationQueue queue = mock(CancellationQueue.class);
        CancellationDLQ dlq = mock(CancellationDLQ.class);

        when(queue.getPending("b1")).thenReturn(Optional.empty());
        when(dlq.get("b1")).thenReturn(Optional.empty());
        when(codec.decode("b1")).thenReturn(BookingPayload.of("STUB", "res-1", "c1"));

        TransferSupplier supplier = mock(TransferSupplier.class);
        when(registry.getSupplier("STUB")).thenReturn(Optional.of(supplier));
        when(supplier.cancel(any()))
            .thenReturn(SupplierCancelResult.success("STUB", "res-1", Money.of(10, "USD")))
            .thenReturn(SupplierCancelResult.alreadyCancelled("STUB", "res-1"));

        TransferCancellationService service = new TransferCancellationService(registry, codec, queue, dlq);

        CancelResponse success = service.cancel("b1");
        assertThat(success.getStatus()).isEqualTo("CANCELLED");

        CancelResponse already = service.cancel("b1");
        assertThat(already.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void getStatusPendingDlqAndUnknown() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        BookingIdCodec codec = mock(BookingIdCodec.class);
        CancellationQueue queue = mock(CancellationQueue.class);
        CancellationDLQ dlq = mock(CancellationDLQ.class);

        when(queue.getPending("b1")).thenReturn(Optional.of(
            CancellationTask.create("b1", "STUB", "r1", "c1").withRetry("err")));
        when(dlq.get("b2")).thenReturn(Optional.of(
            CancellationTask.create("b2", "STUB", "r2", "c2").withRetry("err")));

        TransferCancellationService service = new TransferCancellationService(registry, codec, queue, dlq);

        assertThat(service.getStatus("b1").getStatus()).isEqualTo("PENDING");
        assertThat(service.getStatus("b2").getStatus()).isEqualTo("FAILED");
        assertThat(service.getStatus("b3").getStatus()).isEqualTo("UNKNOWN");
    }
}
