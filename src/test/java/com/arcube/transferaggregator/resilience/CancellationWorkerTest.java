package com.arcube.transferaggregator.resilience;

import com.arcube.transferaggregator.domain.CancelCommand;
import com.arcube.transferaggregator.domain.Money;
import com.arcube.transferaggregator.ports.SupplierCancelResult;
import com.arcube.transferaggregator.ports.SupplierRegistry;
import com.arcube.transferaggregator.ports.TransferSupplier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CancellationWorkerTest {

    @Test
    void movesExpiredTaskToDlq() {
        CancellationQueue queue = new CancellationQueue();
        CancellationDLQ dlq = new CancellationDLQ(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        CancellationTask expired = new CancellationTask(
            "b1", "STUB", "r1", "c1",
            Instant.now().minusSeconds(3601), 0, null
        );
        queue.enqueue(expired);

        SupplierRegistry registry = new SupplierRegistry() {
            @Override
            public java.util.List<TransferSupplier> getEnabledSuppliers() {
                return List.of();
            }

            @Override
            public java.util.Optional<TransferSupplier> getSupplier(String supplierCode) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.List<TransferSupplier> getAllSuppliers() {
                return List.of();
            }
        };

        CancellationWorker worker = new CancellationWorker(queue, dlq, registry);
        worker.processCancellations();

        assertThat(dlq.get("b1")).isPresent();
        assertThat(queue.getPending("b1")).isEmpty();
    }

    @Test
    void retriesAndEventuallySendsToDlq() {
        CancellationQueue queue = new CancellationQueue();
        CancellationDLQ dlq = new CancellationDLQ(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        CancellationTask task = CancellationTask.create("b2", "STUB", "r2", "c2");
        queue.enqueue(task);

        TransferSupplier supplier = new TransferSupplier() {
            @Override
            public String getSupplierCode() {
                return "STUB";
            }

            @Override
            public String getSupplierName() {
                return "Stub";
            }

            @Override
            public com.arcube.transferaggregator.ports.SupplierSearchResult search(
                com.arcube.transferaggregator.domain.SearchCommand command, Duration timeout) {
                throw new UnsupportedOperationException("not needed");
            }

            @Override
            public com.arcube.transferaggregator.ports.SupplierBookingResult book(
                com.arcube.transferaggregator.domain.BookCommand command, Duration timeout) {
                throw new UnsupportedOperationException("not needed");
            }

            @Override
            public SupplierCancelResult cancel(CancelCommand command) {
                return SupplierCancelResult.failed("STUB", command.reservationId(), "FAIL", "fail");
            }

            @Override
            public boolean isEnabled() {
                return true;
            }
        };

        SupplierRegistry registry = new SupplierRegistry() {
            @Override
            public java.util.List<TransferSupplier> getEnabledSuppliers() {
                return List.of(supplier);
            }

            @Override
            public java.util.Optional<TransferSupplier> getSupplier(String supplierCode) {
                return java.util.Optional.of(supplier);
            }

            @Override
            public java.util.List<TransferSupplier> getAllSuppliers() {
                return List.of(supplier);
            }
        };

        CancellationWorker worker = new CancellationWorker(queue, dlq, registry);
        worker.processCancellations();
        worker.processCancellations();
        worker.processCancellations();
        worker.processCancellations();

        assertThat(dlq.get("b2")).isPresent();
        assertThat(queue.getPending("b2")).isEmpty();
    }

    @Test
    void handlesSupplierNotFound() {
        CancellationQueue queue = new CancellationQueue();
        CancellationDLQ dlq = new CancellationDLQ(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        CancellationTask task = CancellationTask.create("b3", "MISSING", "r3", "c3");
        queue.enqueue(task);

        SupplierRegistry registry = new SupplierRegistry() {
            @Override
            public java.util.List<TransferSupplier> getEnabledSuppliers() {
                return List.of();
            }

            @Override
            public java.util.Optional<TransferSupplier> getSupplier(String supplierCode) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.List<TransferSupplier> getAllSuppliers() {
                return List.of();
            }
        };

        CancellationWorker worker = new CancellationWorker(queue, dlq, registry);
        worker.processCancellations();

        assertThat(dlq.get("b3")).isPresent();
        assertThat(queue.getPending("b3")).isEmpty();
    }

    @Test
    void completesWhenCancellationSucceeds() {
        CancellationQueue queue = new CancellationQueue();
        CancellationDLQ dlq = new CancellationDLQ(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        CancellationTask task = CancellationTask.create("b4", "STUB", "r4", "c4");
        queue.enqueue(task);

        TransferSupplier supplier = new TransferSupplier() {
            @Override
            public String getSupplierCode() {
                return "STUB";
            }

            @Override
            public String getSupplierName() {
                return "Stub";
            }

            @Override
            public com.arcube.transferaggregator.ports.SupplierSearchResult search(
                com.arcube.transferaggregator.domain.SearchCommand command, Duration timeout) {
                throw new UnsupportedOperationException("not needed");
            }

            @Override
            public com.arcube.transferaggregator.ports.SupplierBookingResult book(
                com.arcube.transferaggregator.domain.BookCommand command, Duration timeout) {
                throw new UnsupportedOperationException("not needed");
            }

            @Override
            public SupplierCancelResult cancel(CancelCommand command) {
                return SupplierCancelResult.success("STUB", command.reservationId(), Money.of(5, "USD"));
            }

            @Override
            public boolean isEnabled() {
                return true;
            }
        };

        SupplierRegistry registry = new SupplierRegistry() {
            @Override
            public java.util.List<TransferSupplier> getEnabledSuppliers() {
                return List.of(supplier);
            }

            @Override
            public java.util.Optional<TransferSupplier> getSupplier(String supplierCode) {
                return java.util.Optional.of(supplier);
            }

            @Override
            public java.util.List<TransferSupplier> getAllSuppliers() {
                return List.of(supplier);
            }
        };

        CancellationWorker worker = new CancellationWorker(queue, dlq, registry);
        worker.processCancellations();

        assertThat(queue.getPending("b4")).isEmpty();
        assertThat(dlq.get("b4")).isEmpty();
    }

    @Test
    void handlesCancelExceptionAndRetries() {
        CancellationQueue queue = spy(new CancellationQueue());
        CancellationDLQ dlq = new CancellationDLQ(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        CancellationTask task = CancellationTask.create("b5", "STUB", "r5", "c5");
        doReturn(java.util.Optional.of(task)).doReturn(java.util.Optional.empty()).when(queue).poll();

        TransferSupplier supplier = new TransferSupplier() {
            @Override
            public String getSupplierCode() {
                return "STUB";
            }

            @Override
            public String getSupplierName() {
                return "Stub";
            }

            @Override
            public com.arcube.transferaggregator.ports.SupplierSearchResult search(
                com.arcube.transferaggregator.domain.SearchCommand command, Duration timeout) {
                throw new UnsupportedOperationException("not needed");
            }

            @Override
            public com.arcube.transferaggregator.ports.SupplierBookingResult book(
                com.arcube.transferaggregator.domain.BookCommand command, Duration timeout) {
                throw new UnsupportedOperationException("not needed");
            }

            @Override
            public SupplierCancelResult cancel(CancelCommand command) {
                throw new RuntimeException("boom");
            }

            @Override
            public boolean isEnabled() {
                return true;
            }
        };

        SupplierRegistry registry = new SupplierRegistry() {
            @Override
            public java.util.List<TransferSupplier> getEnabledSuppliers() {
                return List.of(supplier);
            }

            @Override
            public java.util.Optional<TransferSupplier> getSupplier(String supplierCode) {
                return java.util.Optional.of(supplier);
            }

            @Override
            public java.util.List<TransferSupplier> getAllSuppliers() {
                return List.of(supplier);
            }
        };

        CancellationWorker worker = new CancellationWorker(queue, dlq, registry);
        worker.processCancellations();

        assertThat(dlq.get("b5")).isEmpty();
    }

    @Test
    void handlesProcessTaskFailureInLoop() {
        CancellationQueue queue = spy(new CancellationQueue());
        CancellationDLQ dlq = new CancellationDLQ(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        CancellationTask badTask = new CancellationTask(
            "b6", "STUB", "r6", "c6", null, 0, null
        );
        doReturn(java.util.Optional.of(badTask)).doReturn(java.util.Optional.empty()).when(queue).poll();

        SupplierRegistry registry = new SupplierRegistry() {
            @Override
            public java.util.List<TransferSupplier> getEnabledSuppliers() {
                return List.of();
            }

            @Override
            public java.util.Optional<TransferSupplier> getSupplier(String supplierCode) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.List<TransferSupplier> getAllSuppliers() {
                return List.of();
            }
        };

        CancellationWorker worker = new CancellationWorker(queue, dlq, registry);
        worker.processCancellations();

        assertThat(dlq.get("b6")).isEmpty();
    }
}
