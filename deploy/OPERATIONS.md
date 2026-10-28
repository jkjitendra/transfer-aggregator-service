# Build and operate the transfer service

## Local container stack

From a fresh checkout, install Docker with Compose, then run:

```bash
test -f deploy/local/.env || bash scripts/generate-local-env.sh
docker-compose --env-file deploy/local/.env -f deploy/local/compose.yml up -d --build
curl -fsS http://localhost:8080/actuator/health/readiness
```

The generated `deploy/local/.env` is ignored by Git and contains local-only random keys. The stack runs PostgreSQL, Redis, a one-time Flyway migration container, Keycloak, and the API. It binds the API and Keycloak to loopback. Start a second API instance with `docker-compose --env-file deploy/local/.env -f deploy/local/compose.yml --profile replicas up -d --build`; it listens on port 8082 and shares the same PostgreSQL and Redis. Stop with `docker-compose --env-file deploy/local/.env -f deploy/local/compose.yml down`. Add `--volumes` only when deliberately deleting local databases and Keycloak state.
PostgreSQL and Redis are also exposed on loopback ports 55435 and 56379 for local integration tests. Set `TRANSFER_TEST_POSTGRES_URL`, `TRANSFER_TEST_POSTGRES_USER`, `TRANSFER_TEST_POSTGRES_PASSWORD`, `TRANSFER_TEST_REDIS_HOST`, `TRANSFER_TEST_REDIS_PORT`, and `TRANSFER_TEST_REDIS_PASSWORD` from the generated local credentials before `./mvnw -Pstatic-analysis clean verify`. CI supplies its own isolated service credentials.

Keycloak imports the `transfer-local` realm and `transfer-local-cli` public client. The API validates `iss`, `aud=transfer-api`, and the tenant and role claims. Its JWK URL points to Keycloak's internal service address while the expected issuer remains the browser-facing `localhost` URL. The imported client supplies the `default` tenant claim. The direct grant is for loopback development only; shared deployments must use the organization's OIDC provider and approved sign-in flow.

### Run the authenticated operations console locally

1. From the repository root, start the stack with the commands above. If `deploy/local/.env` already exists, keep it; `generate-local-env.sh` refuses to replace existing local keys. Check that `curl -fsS http://localhost:8080/actuator/health/readiness` returns `{"status":"UP"}`.
2. Open [Keycloak administration](http://localhost:8081/admin/) and sign in as `local-admin` using `KEYCLOAK_ADMIN_PASSWORD` from the ignored `deploy/local/.env` file. In the realm selector choose **transfer-local**. Under **Users**, create an enabled local user with a username, email, first name, and last name. In **Credentials**, set a non-temporary password. In **Role mapping**, assign the **OPERATOR** realm role for the full console. Use **SUPPORT** for read-only queue and review access or **CUSTOMER** for tenant-owned operation lookup. Do not paste passwords or tokens into tickets or chat.
3. Copy a short-lived access token to the clipboard without putting the password or token in shell history or terminal output:

   ```bash
   python3 scripts/copy-local-console-token.py
   ```

   Enter that user's Keycloak username and password at the prompts. The helper calls only the loopback `transfer-local-cli` token endpoint and uses `pbcopy`, `wl-copy`, or `xclip`; install one of those clipboard commands if none is available.
4. Open [the visual UI](http://localhost:8080/visual/index.html), select **Authenticated operations console**, paste the token into **OIDC access token**, and click **Connect**. Expect `Connected · tenant default · OPERATOR` (or the role you assigned). The field clears immediately; the token stays in this tab's memory until you disconnect, refresh, or close it. After pasting on macOS, clear the clipboard with `printf '' | pbcopy`.
5. With **OPERATOR**, click **Refresh queues** and **Refresh snapshot**. Review the cancellation queue, DLQ, recent history, ambiguous writes, and aggregate metrics. Enter an operation UUID from an API write to load its sanitized status and timeline. Queues can be empty and counters can be zero on a fresh stack: the public seeded architecture demo does not create live database records. To act on a dead letter, open its timeline, verify the supplier outcome externally, click **Replay** or **Resolve**, enter a safe evidence reference in the inline field, then click **Record Replay** or **Record Resolve**. Replay is offered only for configured suppliers with idempotent cancellation, and the server checks again before accepting it. The Grafana link needs the separate [observability stack](../observability/OPERATIONS.md); its historical metrics also need a scraper identity.

If an older local stack already owns ports `8080`/`8081`, reuse its Compose project instead of launching a second copy. For example, with a project named `transfer-phase5-local`, this starts only the newly built API image while leaving its PostgreSQL, Redis, and Keycloak containers running:

```bash
docker-compose -p transfer-phase5-local --env-file deploy/local/.env -f deploy/local/compose.yml up -d --build --no-deps app
```

If the token helper reports **Account is not fully set up**, open **Users → the local user → Details**, fill both **First name** and **Last name**, and save. Clear any pending **Required user actions** after completing them, and ensure the password is non-temporary. Then run the helper again. If **Connect** reports an expired or rejected token, request a fresh token. A `403` means the token lacks the required realm role or has a tenant claim mismatch. Select the intended realm and assign the role there, then request a new token; changing a role does not update an existing token.

LocalStack is optional: add `--profile aws` to the Compose command to run the pinned 4.14.0 open-source community image on port 4566, or use your existing installation. This archived version receives no further fixes and is only for local emulation. The API does not currently call AWS services; LocalStack is an integration sandbox, not a claim of S3/SQS production behavior. The separate [observability stack](../observability/OPERATIONS.md) provides local dashboards and traces. Authenticated metrics require a scraper service identity.

The `local` profile enables mock suppliers, including the intentionally instance-local slow polling simulator, and overrides the live-supplier startup guard solely for this loopback stack. A poll on another replica can read the Redis aggregate but cannot advance that simulator's private pending state. It must not be used for shared or customer traffic. Production manifests enable `prod`, disable every mock, and keep the live-supplier guard enabled. They will fail to start until a verified live booking supplier is implemented and configured.

## CI and release artifacts

The GitHub workflow runs OpenAPI structural checks, a full Maven verify including PostgreSQL and Redis integration tests, SpotBugs, Kustomize rendering, an application image build, a migration image build, source secret/configuration scans, a Maven CycloneDX SBOM vulnerability scan, and critical operating-system vulnerability scans of both built images. Scanning the generated SBOM avoids Maven Central rate limits from resolving the source POM a second time. The two images use the Git commit SHA as their candidate tag. A tag is not sufficient to identify a release: record and deploy the registry's immutable `sha256` digest for both images. CI builds candidates but does not publish images or deploy them.

Before a release, review scan findings, contract test results, schema changes, supplier capabilities, and the latest backup and restore drill. Publish the two approved images to your registry using your normal release identity, then resolve their digests. Deploy the reviewed digests; do not rebuild during a rollout.

The Kubernetes manifests are rendered with `scripts/render-k8s-release.sh`. Set `TRANSFER_APP_IMAGE` and `TRANSFER_MIGRATION_IMAGE` to complete `registry/repository@sha256:<64 hex>` references. The script rejects mutable tags and only prints YAML. It does not apply changes. For example:

```bash
export TRANSFER_APP_IMAGE='registry.example/transfer-aggregator@sha256:<64 hex>'
export TRANSFER_MIGRATION_IMAGE='registry.example/transfer-aggregator-migrations@sha256:<64 hex>'
bash scripts/render-k8s-release.sh production migration > /tmp/transfer-migration.yaml
bash scripts/render-k8s-release.sh production app > /tmp/transfer-app.yaml
```

Replace the example digests before running. The production overlay uses its own namespace. Application pods run as a non-root user with a read-only filesystem, bounded resources, startup/readiness/liveness probes, rolling updates, an HPA, a disruption budget, and ingress policy. The base ingress policy allows same-namespace callers and namespaces labeled `transfer-aggregator-client=true`; the cluster owner must supply routing, TLS, metrics-server, and production egress restrictions. PostgreSQL, Redis, OIDC, secret distribution, private registry access, and telemetry are external dependencies. Do not expose the service until those are configured and validated.

Create `transfer-runtime` from a private secret source in the target namespace. `deploy/k8s/transfer-runtime.env.example` lists the required keys but contains no credentials. Protect the real file and never commit it. The production OIDC issuer and optional JWK endpoint must use HTTPS. Set `TOKEN_SECRET` to at least 32 random bytes and `WORKFLOW_ENCRYPTION_KEY` to a base64-encoded 32-byte key. Preserve both through the entire data and backup retention window. The default tenant starts disabled; enable it only after its supplier and OIDC tenant claim have been verified.

After reviewing the rendered YAML and establishing the namespace and Secret, run the migration **before** deploying the app:

```bash
kubectl delete job transfer-migrate -n transfer-production --ignore-not-found
kubectl apply -f /tmp/transfer-migration.yaml
kubectl wait --for=condition=complete job/transfer-migrate -n transfer-production --timeout=6m
kubectl apply -f /tmp/transfer-app.yaml
kubectl rollout status deployment/transfer-aggregator -n transfer-production --timeout=6m
```

Check readiness, error rate, unknown outcomes, queue age, DLQ size, traces, and the operation timeline. The migration job is deliberately separate from the Deployment so a rollout cannot race schema creation. Keep migrations backward compatible with the previous application image. If application rollout fails, use `kubectl rollout undo deployment/transfer-aggregator` in the production namespace and inspect the rollout; do not automatically reverse a database migration. Pause rollout when database compatibility is uncertain.

## Backup, restore, and incident actions

Back up PostgreSQL with `pg_dump --format=custom` and verify the archive can be restored with `pg_restore` into an **isolated** PostgreSQL instance. Record the schema version (`flyway_schema_history`), operation/idempotency/cancellation/DLQ/audit counts, and a read-only operation timeline before directing traffic to a restored instance. Encrypt database volumes and backup archives; restrict backup access. Retain the workflow encryption key and every HMAC verification key needed for records within the restore window. Redis contains expiring search state and rate-limit counters; after Redis loss, ask clients to repeat expired searches. The detailed alert and DLQ response steps are in the [observability runbook](../observability/OPERATIONS.md).

For an incident, first check liveness versus readiness, PostgreSQL and Redis connectivity, and the current supplier circuit states. Locate a write by operation ID and its sanitized audit timeline. Treat `UNKNOWN_OUTCOME` as a reconciliation case; do not blindly repeat the supplier write. For cancellation DLQ work, use the authenticated operator replay/resolve API and retain its audit event. Never clear a metric as a substitute for resolving a job.

Rotate HMAC signing keys by issuing new IDs with a new active key ID and retaining the old key in `security.token.previous-keys` until old IDs and backups have expired. Test verification of old and new IDs before retiring the old key. The workflow payload encryption format currently has one active key and no automatic re-encryption path; changing that key without a controlled data migration makes stored payloads unreadable. Treat encryption-key rotation as a separately rehearsed migration.

## Supplier onboarding and release limits

New supplier adapters must implement the port contract and document booking/cancellation idempotency, status lookup, changes, timeouts, and error mapping. Validate request and response schemas against the supplier's published sandbox contract, including ambiguous write outcomes, before enabling the adapter in production. Route estimates do not meet the live booking supplier requirement.

This repository currently lacks a verified live booking supplier. Therefore the production startup guard intentionally blocks the production overlay. A clean checkout and local stack can be exercised now; a real production rollout requires an approved supplier and environment.
