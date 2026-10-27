package com.arcube.transferaggregator.workflow;

import com.arcube.transferaggregator.adapters.idcodec.BookingPayload;
import com.arcube.transferaggregator.config.TenantContext;
import com.arcube.transferaggregator.domain.CancelCommand;
import com.arcube.transferaggregator.dto.CancelResponse;
import com.arcube.transferaggregator.exception.CancellationRequestNotFoundException;
import com.arcube.transferaggregator.exception.SupplierNotFoundException;
import com.arcube.transferaggregator.ports.IdempotentCancellationSupplier;
import com.arcube.transferaggregator.ports.SupplierCancelResult;
import com.arcube.transferaggregator.ports.SupplierRegistry;
import com.arcube.transferaggregator.ports.TransferSupplier;
import com.arcube.transferaggregator.resilience.SupplierOperationPolicy;
import com.arcube.transferaggregator.config.AggregatorProperties;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Persists cancellation before acknowledgement and processes leased jobs in bounded batches. */
@Slf4j
@Service
@Profile("local | prod")
public class DurableCancellationWorkflow {
    private final CancellationRepository repository;
    private final SupplierRegistry registry;
    private final int maxAttempts;
    private final AggregatorProperties properties;
    private final ObservationRegistry observations;
    private final int leaseSeconds;

    public DurableCancellationWorkflow(CancellationRepository repository, SupplierRegistry registry,
                                       @Value("${workflow.cancellation-max-attempts:3}") int maxAttempts,
                                       AggregatorProperties properties, ObservationRegistry observations,
                                       @Value("${workflow.cancellation-lease-seconds:30}") int leaseSeconds) {
        this.repository = repository;
        this.registry = registry;
        if (maxAttempts < 1 || maxAttempts > 20) throw new IllegalStateException("Invalid cancellation attempt limit");
        if (leaseSeconds < 10 || leaseSeconds > 3600) throw new IllegalStateException("Invalid cancellation lease");
        this.maxAttempts = maxAttempts;
        this.properties = properties;
        this.observations = observations;
        this.leaseSeconds = leaseSeconds;
    }

    public CancelResponse request(String bookingId, BookingPayload payload) {
        String tenant = tenant();
        registry.getSupplier(payload.supplierCode())
            .orElseThrow(() -> new SupplierNotFoundException(payload.supplierCode()));
        CancellationRepository.Job job = repository.request(tenant, bookingId,
            payload.supplierCode(), payload.reservationId());
        return statusFor(job, bookingId);
    }

    public CancelResponse status(String bookingId) {
        CancellationRepository.Job job = repository.find(tenant(), WorkflowRepository.sha256(bookingId))
            .orElseThrow(CancellationRequestNotFoundException::new);
        return statusFor(job, bookingId);
    }

    public List<CancellationRepository.DeadLetter> deadLetters(int limit) {
        return repository.deadLetters(limit);
    }

    public boolean replay(UUID jobId, String actor, String evidenceReference) {
        validateEvidence(evidenceReference);
        CancellationRepository.Job job = repository.find(jobId).orElseThrow(CancellationRequestNotFoundException::new);
        TransferSupplier supplier = registry.getSupplier(job.supplierCode())
            .orElseThrow(() -> new SupplierNotFoundException(job.supplierCode()));
        if (!(supplier instanceof IdempotentCancellationSupplier)) {
            throw new IllegalArgumentException("Supplier cancellation replay is not verified safe");
        }
        return repository.replay(jobId, actor, evidenceReference);
    }

    public boolean resolve(UUID jobId, String actor, String evidenceReference) {
        validateEvidence(evidenceReference);
        return repository.resolve(jobId, actor, evidenceReference);
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void processDue() {
        for (CancellationRepository.Job expired : repository.expiredLeases()) {
            boolean safe = registry.getSupplier(expired.supplierCode())
                .filter(IdempotentCancellationSupplier.class::isInstance).isPresent();
            repository.recover(expired, safe, maxAttempts);
        }
        List<CancellationRepository.Job> jobs = repository.claimDue(20);
        if (!jobs.isEmpty()) {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                CompletableFuture<?>[] attempts = jobs.stream()
                    .map(job -> CompletableFuture.runAsync(() -> process(job), executor))
                    .toArray(CompletableFuture[]::new);
                CompletableFuture.allOf(attempts).join();
            }
        }
    }

    private void process(CancellationRepository.Job job) {
        MDC.put("operationId", job.operationId().toString());
        try {
            Observation.createNotStarted("transfer.cancellation.attempt", observations)
                .lowCardinalityKeyValue("supplier", job.supplierCode())
                .highCardinalityKeyValue("operation.id", job.operationId().toString())
                .observe(() -> processAttempt(job));
        } finally {
            MDC.clear();
        }
    }

    private void processAttempt(CancellationRepository.Job job) {
        TransferSupplier supplier = registry.getSupplier(job.supplierCode()).orElse(null);
        if (supplier == null) {
            repository.failedAttempt(job, true, true, maxAttempts, "SUPPLIER_UNAVAILABLE");
            return;
        }
        boolean safeToRetry = supplier instanceof IdempotentCancellationSupplier;
        long timeoutMillis = Math.min(
            SupplierOperationPolicy.timeout(properties, job.supplierCode(),
                SupplierOperationPolicy.Operation.CANCELLATION, null).toMillis(),
            Math.max(1000, (leaseSeconds - 5L) * 1000));
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        Observation parent = observations.getCurrentObservation();
        try {
            Future<SupplierCancelResult> future = executor.submit(() -> {
                TenantContext.setTenantId(job.tenantId());
                MDC.put("operationId", job.operationId().toString());
                try {
                    java.util.function.Supplier<SupplierCancelResult> call = () ->
                        supplier.cancel(CancelCommand.of(repository.reservationId(job), job.supplierCode()));
                    return Observation.createNotStarted("transfer.cancellation.supplier", observations)
                        .parentObservation(parent)
                        .lowCardinalityKeyValue("supplier", job.supplierCode())
                        .highCardinalityKeyValue("operation.id", job.operationId().toString())
                        .observe(call::get);
                } finally {
                    TenantContext.clear();
                    MDC.clear();
                }
            });
            SupplierCancelResult result;
            try {
                result = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                future.cancel(true);
                repository.failedAttempt(job, safeToRetry, false, maxAttempts, "SUPPLIER_TIMEOUT");
                return;
            }
            if (result.isSuccess()) {
                CancelResponse response = result.alreadyCancelled()
                    ? CancelResponse.alreadyCancelled(null) : CancelResponse.success(null, result.refundAmount());
                response.setOperationId(job.operationId().toString());
                repository.succeeded(job, response);
            } else {
                boolean permanent = "TOO_LATE_TO_CANCEL".equals(result.errorCode());
                repository.failedAttempt(job, safeToRetry, permanent, maxAttempts,
                    permanent ? "TOO_LATE_TO_CANCEL" : "SUPPLIER_REJECTED");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            repository.failedAttempt(job, safeToRetry, false, maxAttempts, "INTERRUPTED");
        } catch (ExecutionException exception) {
            log.warn("Cancellation attempt ended without a verified supplier result");
            repository.failedAttempt(job, safeToRetry, false, maxAttempts, "SUPPLIER_ERROR");
        } finally {
            executor.shutdownNow();
        }
    }

    private CancelResponse statusFor(CancellationRepository.Job job, String bookingId) {
        if ("SUCCEEDED".equals(job.state())) {
            CancelResponse response = repository.storedResponse(job).orElseThrow();
            response.setBookingId(bookingId);
            response.setOperationId(job.operationId().toString());
            return response;
        }
        CancelResponse response = switch (job.state()) {
            case "DLQ" -> CancelResponse.failed(bookingId, "Cancellation needs operator review");
            case "UNKNOWN_OUTCOME" -> CancelResponse.builder().bookingId(bookingId)
                .status("UNKNOWN_OUTCOME").message("Supplier outcome is being reviewed").build();
            case "RESOLVED" -> CancelResponse.builder().bookingId(bookingId)
                .status("RESOLVED").message("Operator marked this request resolved").build();
            default -> CancelResponse.pending(bookingId, "Cancellation is queued or in progress");
        };
        response.setOperationId(job.operationId().toString());
        return response;
    }

    private String tenant() {
        String tenant = TenantContext.getTenantId();
        if (tenant == null) throw new IllegalStateException("Authenticated tenant context is required");
        return tenant;
    }

    private void validateEvidence(String reference) {
        if (reference == null || !reference.matches("[A-Za-z0-9._:/-]{4,128}")) {
            throw new IllegalArgumentException("An evidence reference of 4-128 safe characters is required");
        }
    }
}
