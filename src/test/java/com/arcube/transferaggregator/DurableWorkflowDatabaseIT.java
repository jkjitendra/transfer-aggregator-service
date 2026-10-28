package com.arcube.transferaggregator;

import com.arcube.transferaggregator.workflow.CancellationRepository;
import com.arcube.transferaggregator.workflow.EnvelopeCrypto;
import com.arcube.transferaggregator.workflow.IdempotencyConflictException;
import com.arcube.transferaggregator.workflow.WorkflowRepository;
import com.arcube.transferaggregator.workflow.DurableWriteCoordinator;
import com.arcube.transferaggregator.workflow.DurableCancellationWorkflow;
import com.arcube.transferaggregator.observability.WorkflowQueueMetrics;
import com.arcube.transferaggregator.observability.WorkflowMetrics;
import com.arcube.transferaggregator.observability.StorageTelemetry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import com.arcube.transferaggregator.config.TenantContext;
import com.arcube.transferaggregator.config.AggregatorProperties;
import com.arcube.transferaggregator.adapters.idcodec.BookingPayload;
import com.arcube.transferaggregator.dto.BookRequest;
import com.arcube.transferaggregator.dto.BookResponse;
import com.arcube.transferaggregator.ports.IdempotentCancellationSupplier;
import com.arcube.transferaggregator.ports.SupplierCancelResult;
import com.arcube.transferaggregator.ports.SupplierRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.MDC;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest(properties = {
    "security.api.issuer-uri=https://issuer.example.test",
    "security.api.audience=transfer-api",
    "security.token.secret=test-only-key-not-for-production-123456789012345",
    "transfer.aggregator.require-live-supplier=false",
    "workflow.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@ActiveProfiles("prod")
@EnabledIfEnvironmentVariable(named = "TRANSFER_TEST_POSTGRES_URL", matches = ".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DurableWorkflowDatabaseIT {
    private static UUID restartOperation;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TRANSFER_TEST_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TRANSFER_TEST_POSTGRES_USER", "transfer_test"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TRANSFER_TEST_POSTGRES_PASSWORD", ""));
        registry.add("spring.data.redis.host", () -> System.getenv().getOrDefault("TRANSFER_TEST_REDIS_HOST", "localhost"));
        registry.add("spring.data.redis.password", () -> System.getenv().getOrDefault("TRANSFER_TEST_REDIS_PASSWORD", ""));
    }

    @Autowired WorkflowRepository workflows;
    @Autowired CancellationRepository cancellations;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired EnvelopeCrypto crypto;
    @Autowired ObjectMapper mapper;
    @Autowired DurableWriteCoordinator coordinator;
    @Autowired WorkflowQueueMetrics queueMetrics;
    @Autowired MeterRegistry meters;
    @Autowired Flyway flyway;
    @MockitoBean JwtDecoder decoder;

    @Test
    @Order(1)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void atomicClaimAcrossConcurrentCallersSurvivesContextRestart() throws Exception {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        String key = UUID.randomUUID().toString();
        String fingerprint = WorkflowRepository.sha256("request-a");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<WorkflowRepository.Claim> first = executor.submit(() ->
                workflows.claim("tenant-a", "BOOKING", key, fingerprint, "STUB"));
            Future<WorkflowRepository.Claim> second = executor.submit(() ->
                workflows.claim("tenant-a", "BOOKING", key, fingerprint, "STUB"));
            List<WorkflowRepository.Claim> claims = List.of(first.get(), second.get());
            assertThat(claims.stream().filter(WorkflowRepository.Claim::acquired)).hasSize(1);
            assertThat(claims.get(0).operation().id()).isEqualTo(claims.get(1).operation().id());
            restartOperation = claims.get(0).operation().id();
        }
        assertThatThrownBy(() -> workflows.claim("tenant-a", "BOOKING", key,
            WorkflowRepository.sha256("different"), "STUB"))
            .isInstanceOf(IdempotencyConflictException.class);
        workflows.complete(restartOperation, "tenant-a", "CONFIRMED", "{\"status\":\"CONFIRMED\"}");
        assertThat(workflows.getForTenant(restartOperation, "tenant-b")).isEmpty();
        MDC.put("traceId", "a".repeat(32));
        MDC.put("requestId", "request-123");
        try {
            workflows.audit(restartOperation, "tenant-a", "system", "REVIEWED",
                "EVIDENCE_RECORDED:private-ticket-ref");
        } finally {
            MDC.clear();
        }
        assertThat(workflows.timeline(restartOperation, 100)).anySatisfy(event -> {
            assertThat(event.action()).isEqualTo("REVIEWED");
            assertThat(event.detailCode()).isEqualTo("EVIDENCE_RECORDED");
            assertThat(event.traceId()).isEqualTo("a".repeat(32));
            assertThat(event.requestId()).isEqualTo("request-123");
        });
    }

    @Test
    @Order(2)
    void durableStatusAndLeasedQueueSurviveContextRestart() {
        assertThat(workflows.getForTenant(restartOperation, "tenant-a")).isPresent()
            .get().extracting(WorkflowRepository.Operation::state).isEqualTo("CONFIRMED");

        String bookingId = "booking-" + UUID.randomUUID();
        CancellationRepository.Job created = cancellations.request("tenant-a", bookingId, "STUB", "sensitive-reservation");
        CancellationRepository secondReplica = new CancellationRepository(
            jdbc, transactions, crypto, workflows, mapper, 30, 90,
            new WorkflowMetrics(meters), new StorageTelemetry(ObservationRegistry.NOOP));
        assertThat(secondReplica.find("tenant-a", WorkflowRepository.sha256(bookingId))).isPresent();
        assertThat(secondReplica.find("tenant-b", WorkflowRepository.sha256(bookingId))).isEmpty();
        assertThat(new String(created.reservationCiphertext())).doesNotContain("sensitive-reservation");
        assertThat(cancellations.queue(100)).anyMatch(job -> job.jobId().equals(created.id()));
        assertThat(cancellations.history(100)).anyMatch(job -> job.jobId().equals(created.id()));

        CancellationRepository.Job claimed = secondReplica.claimDue(20).stream()
            .filter(job -> job.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(cancellations.claimDue(20)).noneMatch(job -> job.id().equals(created.id()));
        secondReplica.failedAttempt(claimed, true, false, 3, "TRANSIENT");
        assertThat(cancellations.find(created.id())).get()
            .extracting(CancellationRepository.Job::state).isEqualTo("RETRY_SCHEDULED");

        jdbc.update("UPDATE cancellation_jobs SET next_attempt_at=now() WHERE id=?", created.id());
        CancellationRepository.Job secondClaim = cancellations.claimDue(20).stream()
            .filter(job -> job.id().equals(created.id())).findFirst().orElseThrow();
        cancellations.failedAttempt(secondClaim, true, true, 3, "PERMANENT");
        assertThat(cancellations.deadLetters(100)).anyMatch(letter -> letter.jobId().equals(created.id()));
        queueMetrics.refresh();
        assertThat(meters.get("transfer.cancellation.dlq.depth").gauge().value()).isGreaterThanOrEqualTo(1);
        assertThat(cancellations.replay(created.id(), "operator", "ticket-1234")).isTrue();
        assertThat(cancellations.deadLetters(100)).noneMatch(letter -> letter.jobId().equals(created.id()));
        assertThat(cancellations.history(100)).anyMatch(job ->
            job.jobId().equals(created.id()) && "RETRY_SCHEDULED".equals(job.state()));
        assertThat(workflows.timeline(created.operationId(), 100))
            .anyMatch(event -> "DLQ_REPLAY".equals(event.action()));
        queueMetrics.refresh();
        assertThat(meters.get("transfer.workflow.snapshot.healthy").gauge().value()).isEqualTo(1);
    }

    @Test
    @Order(3)
    void oneSupplierWriteForConcurrentSameKeyRequests() throws Exception {
        BookRequest request = BookRequest.builder().offerId("offer-for-fingerprint").build();
        String key = UUID.randomUUID().toString();
        AtomicInteger supplierCalls = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            java.util.concurrent.Callable<BookResponse> call = () -> {
                TenantContext.setTenantId("tenant-a");
                try {
                    return coordinator.book(request, key, "STUB", operationId -> {
                        supplierCalls.incrementAndGet();
                        try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        return BookResponse.confirmed("booking-1", "confirmation-1", null, null, null);
                    });
                } finally {
                    TenantContext.clear();
                }
            };
            Future<BookResponse> first = executor.submit(call);
            Future<BookResponse> second = executor.submit(call);
            BookResponse a = first.get();
            BookResponse b = second.get();
            assertThat(a.getOperationId()).isEqualTo(b.getOperationId());
            assertThat(supplierCalls.get()).isEqualTo(1);
        }
    }

    @Test
    @Order(4)
    void durableWorkerStoresTerminalCancellationStatus() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        IdempotentCancellationSupplier supplier = mock(IdempotentCancellationSupplier.class);
        when(registry.getSupplier("SAFE")).thenReturn(Optional.of(supplier));
        CountDownLatch started = new CountDownLatch(2);
        when(supplier.cancel(any())).thenAnswer(invocation -> {
            com.arcube.transferaggregator.domain.CancelCommand command = invocation.getArgument(0);
            started.countDown();
            assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
            return SupplierCancelResult.success("SAFE", command.reservationId(), null);
        });
        DurableCancellationWorkflow workflow = new DurableCancellationWorkflow(cancellations, registry, 3,
            new AggregatorProperties(), ObservationRegistry.NOOP, 30);
        String bookingId = "booking-" + UUID.randomUUID();
        String secondBookingId = "booking-" + UUID.randomUUID();
        TenantContext.setTenantId("tenant-a");
        try {
            assertThat(workflow.request(bookingId, BookingPayload.of("SAFE", "reservation-1", "confirmation-1"))
                .getStatus()).isEqualTo("PENDING");
            assertThat(workflow.request(secondBookingId, BookingPayload.of("SAFE", "reservation-2", "confirmation-2"))
                .getStatus()).isEqualTo("PENDING");
            workflow.processDue();
            assertThat(workflow.status(bookingId).getStatus()).isEqualTo("CANCELLED");
            assertThat(workflow.status(secondBookingId).getStatus()).isEqualTo("CANCELLED");
            assertThat(workflow.status(bookingId).getOperationId()).isNotBlank();
        } finally {
            TenantContext.clear();
        }
    }
}
