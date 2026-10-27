package com.arcube.transferaggregator.controller;

import com.arcube.transferaggregator.config.TenantContext;
import com.arcube.transferaggregator.workflow.DurableCancellationWorkflow;
import com.arcube.transferaggregator.workflow.WorkflowRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DurableOperationsControllerTest {
    @Test
    void timelineIsTenantScopedWhileAdminCanReviewAcrossTenants() {
        WorkflowRepository repository = mock(WorkflowRepository.class);
        DurableOperationsController controller = new DurableOperationsController(
            repository, mock(DurableCancellationWorkflow.class));
        UUID id = UUID.randomUUID();
        var operation = new WorkflowRepository.Operation(id, "tenant-a", "BOOKING", "UNKNOWN_OUTCOME",
            "STUB", null, null, Instant.now(), Instant.now());
        var event = new WorkflowRepository.AuditEvent("CLAIMED", "BOOKING", null, "request-123", Instant.now());
        when(repository.getForTenant(id, "tenant-b")).thenReturn(Optional.empty());
        when(repository.getForTenant(id, "tenant-a")).thenReturn(Optional.of(operation));
        when(repository.get(id)).thenReturn(Optional.of(operation));
        when(repository.timeline(id, 100)).thenReturn(List.of(event));

        try {
            TenantContext.setTenantId("tenant-b");
            assertThat(controller.timeline(id).getStatusCode().value()).isEqualTo(404);
            TenantContext.setTenantId("tenant-a");
            assertThat(controller.timeline(id).getBody()).containsExactly(event);
            assertThat(controller.adminTimeline(id).getBody()).containsExactly(event);
        } finally {
            TenantContext.clear();
        }
    }
}
