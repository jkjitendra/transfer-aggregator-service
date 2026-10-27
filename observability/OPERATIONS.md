# Observability operations

This directory runs a **local observability stack** with Prometheus, Grafana OSS, and Tempo. Grafana shows service and supplier trends after Prometheus has an operator service identity. Tempo shows request and supplier span waterfalls. The authenticated operation timeline API shows durable booking, change, and cancellation transitions. This stack is for local development.

## Start the local view

1. Set a local Grafana password in your shell: `export GRAFANA_ADMIN_PASSWORD=<your-local-password>`.
2. Start the stack: `docker-compose -f observability/docker-compose.yml up -d`.
3. Start the application with `SPRING_PROFILES_ACTIVE=local` and `OTLP_TRACING_ENABLED=true`. The default OTLP URL is `http://localhost:4318/v1/traces`.
4. Open `http://localhost:3000`, sign in as `admin`, and open **Transfer Aggregator Operations**. Use Grafana Explore with the **Tempo** data source to search traces.

Prometheus targets `host.docker.internal:8080/actuator/prometheus`. That endpoint requires an `OPERATOR` bearer token in both supported runtime profiles, so the target remains unauthorized until a service identity is configured for the scraper. A human access token is unsuitable because it expires. Configure an approved OAuth2 client and rotate its credentials through a private secret source before relying on dashboard metrics or alerts. Keep Grafana, Prometheus, and Tempo behind authentication and private networking in any shared environment. The local Tempo volume keeps traces for seven days; Prometheus retains metrics for fifteen days.

## Find an operation

- Start with the opaque `operationId` returned by booking, change, or cancellation. A tenant caller can request `GET /api/v1/operations/{operationId}` and `GET /api/v1/operations/{operationId}/timeline`; another tenant receives 404.
- Support and operators can use `GET /api/v1/admin/operations/review`, `GET /api/v1/admin/cancellations/dlq`, and `GET /api/v1/admin/operations/{operationId}/timeline`.
- Timeline rows contain action, allowlisted detail code, time, request ID, and sampled trace ID. Search Tempo for the trace ID. Search structured logs for `requestId` or `operationId`. Trace IDs can be absent when sampling drops a trace; the durable timeline remains available.
- Do not put names, addresses, tokens, booking IDs, raw supplier payloads, or credentials in an evidence reference, metric label, trace attribute, or support ticket title. Evidence references and actor identifiers are hashed before persistence; the timeline returns only allowlisted codes. Audit events are retained for the configured `workflow.operation-retention-days` (default 90).

## Supplier degradation

Check **Supplier search outcomes**, **Partial search share**, and `TransferSupplierDegraded`. Compare the affected supplier's circuit state and bulkhead saturation with request rate. Use a trace to identify timeout, circuit open, or rejected response. Check supplier status independently before changing its enabled setting. Remote supplier failure does not fail liveness; repeated partial results are an alert and customer experience issue.

## Unknown write outcome

`TransferUnknownOutcome` means a booking or change may have reached a supplier without a confirmed response. Find the operation in the review queue and inspect its timeline and trace. Verify the supplier's authoritative reservation state using the opaque operation reference before deciding. Do not repeat a non-idempotent write. Record an external evidence reference and use the operator review endpoint only after verification; `MANUAL_REVIEW` keeps the item open, while `FAILED` closes a verified failure.

## Stuck cancellations

Check **Oldest pending cancellation**, **Pending cancellations**, worker claim rate, and `TransferCancellationStuck`. Look for lease expiry, Redis/PostgreSQL health, supplier timeouts, and rate limits. The worker uses bounded claims and leases. A non-idempotent ambiguous attempt goes to `UNKNOWN_OUTCOME` for manual reconciliation; it is not automatically retried.

## Dead-letter review

`TransferCancellationDlqNonempty` requires an operator. List the DLQ, open the operation timeline, verify supplier status and reservation outcome, then record an external evidence reference. Replay only if the adapter declares verified idempotent cancellation; the API rejects unsafe replay. Resolve only after the supplier outcome is known. Watch the DLQ gauge return to zero and confirm a new audit transition.

## Service or dependency outage

Check `/actuator/health/liveness` and `/actuator/health/readiness` separately. Production readiness includes PostgreSQL and Redis; liveness covers only the local process. Check `TransferServiceUnavailable`, HTTP error rate, and `TransferWorkflowMetricsStale`. A stale metric snapshot reports `transfer_workflow_snapshot_healthy=0`; queue gauges may retain their last values until PostgreSQL recovers. For a failed deployment, inspect migration and startup logs, revert the application release, and preserve database state for diagnosis. Do not roll back schema migrations blindly.

## Restore and drill

Restore PostgreSQL from a tested backup into an isolated environment, apply the checked-in Flyway migrations, and verify operation, idempotency, cancellation, dead-letter, and audit counts before routing traffic. Preserve the workflow encryption key and HMAC signing keys through the recovery window; losing either makes durable payloads or signed IDs unreadable. Redis search state is short lived, so clients may need to repeat searches after Redis restoration. In an on-call drill, trigger a local cancellation failure, locate it by operation ID, follow the timeline and trace, review its DLQ state, and verify that no customer data appears in metrics or logs.

Prometheus alert rules in `rules.yml` are intentionally local evaluation rules. Connect them to an authenticated notification route and test delivery with the actual on-call team before production use.
