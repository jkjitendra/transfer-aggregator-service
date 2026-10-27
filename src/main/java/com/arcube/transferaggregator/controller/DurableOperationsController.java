package com.arcube.transferaggregator.controller;

import com.arcube.transferaggregator.config.TenantContext;
import com.arcube.transferaggregator.workflow.CancellationRepository;
import com.arcube.transferaggregator.workflow.DurableCancellationWorkflow;
import com.arcube.transferaggregator.workflow.WorkflowRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Tenant-scoped operation status and operator review APIs backed by PostgreSQL. */
@RestController
@Profile("local | prod")
public class DurableOperationsController {
    private final WorkflowRepository operations;
    private final DurableCancellationWorkflow cancellations;

    public DurableOperationsController(WorkflowRepository operations, DurableCancellationWorkflow cancellations) {
        this.operations = operations;
        this.cancellations = cancellations;
    }

    public record OperationView(UUID operationId, String tenantId, String kind, String state,
                                String supplierCode, Instant createdAt, Instant updatedAt) {
        static OperationView from(WorkflowRepository.Operation operation) {
            return new OperationView(operation.id(), operation.tenantId(), operation.kind(), operation.state(),
                operation.supplierCode(), operation.createdAt(), operation.updatedAt());
        }
    }
    public record ActionRequest(String evidenceReference, String decision) {}
    public record DeadLetterView(UUID jobId, UUID operationId, String tenantId, String supplierCode,
                                 int attempts, String reasonCode, Instant createdAt, int replayCount,
                                 boolean replayPermitted) {}

    @GetMapping("/api/v1/operations/{operationId}")
    public ResponseEntity<OperationView> status(@PathVariable UUID operationId) {
        return operations.getForTenant(operationId, TenantContext.getTenantId())
            .map(operation -> ResponseEntity.ok(OperationView.from(operation)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/v1/operations/{operationId}/timeline")
    public ResponseEntity<List<WorkflowRepository.AuditEvent>> timeline(@PathVariable UUID operationId) {
        return operations.getForTenant(operationId, TenantContext.getTenantId())
            .map(operation -> ResponseEntity.ok(operations.timeline(operationId, 100)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/v1/admin/operations/{operationId}")
    public ResponseEntity<OperationView> adminStatus(@PathVariable UUID operationId) {
        return operations.get(operationId)
            .map(operation -> ResponseEntity.ok(OperationView.from(operation)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/v1/admin/operations/{operationId}/timeline")
    public ResponseEntity<List<WorkflowRepository.AuditEvent>> adminTimeline(@PathVariable UUID operationId) {
        return operations.get(operationId)
            .map(operation -> ResponseEntity.ok(operations.timeline(operationId, 100)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/v1/admin/operations/review")
    public List<OperationView> reviewQueue(@RequestParam(defaultValue = "50") int limit) {
        return operations.reviewQueue(bounded(limit)).stream().map(OperationView::from).toList();
    }

    @PostMapping("/api/v1/admin/operations/{operationId}/review")
    public ResponseEntity<Void> review(@PathVariable UUID operationId, @RequestBody ActionRequest request,
                                       Authentication actor) {
        validateEvidence(request.evidenceReference());
        if (!List.of("FAILED", "MANUAL_REVIEW").contains(request.decision())) {
            throw new IllegalArgumentException("Review decision must be FAILED or MANUAL_REVIEW");
        }
        boolean changed = operations.review(operationId, request.decision(), actor.getName(),
            "EVIDENCE_RECORDED:" + WorkflowRepository.sha256(request.evidenceReference()));
        return changed ? ResponseEntity.ok().build() : ResponseEntity.status(409).build();
    }

    @GetMapping("/api/v1/admin/cancellations/dlq")
    public List<DeadLetterView> deadLetters(@RequestParam(defaultValue = "50") int limit) {
        return cancellations.deadLetters(bounded(limit)).stream().map(letter -> new DeadLetterView(
            letter.jobId(), letter.operationId(), letter.tenantId(), letter.supplierCode(),
            letter.attempts(), letter.reasonCode(), letter.createdAt(), letter.replayCount(),
            cancellations.replayPermitted(letter.supplierCode()))).toList();
    }

    @GetMapping("/api/v1/admin/cancellations/queue")
    public List<CancellationRepository.QueueView> cancellationQueue(@RequestParam(defaultValue = "50") int limit) {
        return cancellations.queue(bounded(limit));
    }

    @GetMapping("/api/v1/admin/cancellations/history")
    public List<CancellationRepository.HistoryView> cancellationHistory(@RequestParam(defaultValue = "50") int limit) {
        return cancellations.history(bounded(limit));
    }

    @PostMapping("/api/v1/admin/cancellations/dlq/{jobId}/replay")
    public ResponseEntity<Void> replay(@PathVariable UUID jobId, @RequestBody ActionRequest request,
                                       Authentication actor) {
        return cancellations.replay(jobId, actor.getName(), request.evidenceReference())
            ? ResponseEntity.accepted().build() : ResponseEntity.status(409).build();
    }

    @PostMapping("/api/v1/admin/cancellations/dlq/{jobId}/resolve")
    public ResponseEntity<Void> resolve(@PathVariable UUID jobId, @RequestBody ActionRequest request,
                                        Authentication actor) {
        return cancellations.resolve(jobId, actor.getName(), request.evidenceReference())
            ? ResponseEntity.ok().build() : ResponseEntity.status(409).build();
    }

    private void validateEvidence(String reference) {
        if (reference == null || !reference.matches("[A-Za-z0-9._:/-]{4,128}")) {
            throw new IllegalArgumentException("An evidence reference of 4-128 safe characters is required");
        }
    }

    private int bounded(int limit) {
        return Math.min(Math.max(limit, 1), 100);
    }
}
