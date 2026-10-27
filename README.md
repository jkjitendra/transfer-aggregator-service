# Transfer Aggregator Service

Java 21 and Spring Boot 4 service for searching transfer suppliers, booking offers, changing bookings, and processing cancellations. Supplier adapters implement the `TransferSupplier` port. The API uses signed offer and booking IDs, OIDC bearer tokens, PostgreSQL for durable write workflows, and Redis for expiring search state and distributed rate limits.

**Release status:** the local stack is runnable. No verified live booking supplier is included, so the production startup guard deliberately prevents a production rollout. The mock suppliers create synthetic offers and reservations only in the `local` profile. A route estimate is read-only and does not satisfy the live supplier requirement.

## Configuration

The service has exactly two Spring application configuration files:

| Profile | File | Purpose |
| --- | --- | --- |
| `local` | `src/main/resources/application-local.properties` | Local development with synthetic suppliers and an OIDC-protected API |
| `prod` | `src/main/resources/application-prod.properties` | Production settings with synthetic suppliers disabled and a required live supplier |

Set `SPRING_PROFILES_ACTIVE` explicitly to one of these profiles. The Maven tests supply their own settings in test code. There is no default, demo, staging, or test application properties file. Keep secrets in environment variables or an approved secret manager; never put them in either properties file.

Common required environment variables:

| Variable | Source |
| --- | --- |
| `TOKEN_SECRET` | Generate at least 32 random bytes; keep the same value while signed IDs must remain valid. `openssl rand -hex 48` produces a suitable local value. |
| `WORKFLOW_ENCRYPTION_KEY` | Base64-encode exactly 32 random bytes; retain it while workflow records or backups must be readable. `openssl rand -base64 32` produces a local value. |
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | PostgreSQL connection details. The local Compose stack supplies these. |
| `REDIS_HOST`, `REDIS_PASSWORD` | Redis connection details. The local Compose stack supplies these. |
| `OIDC_ISSUER_URI`, `OIDC_AUDIENCE` | The organization's OIDC provider, or the local Keycloak realm. The audience must match the token's `aud` claim. |

`OIDC_JWK_SET_URI` is optional when issuer discovery is reachable. Set it for local Keycloak if the API runs in a container and the issuer URL resolves differently from the container. The production profile requires HTTPS for both issuer and explicit JWK URLs. Its default tenant is disabled until `DEFAULT_TENANT_ENABLED=true` is set after the tenant's supplier and OIDC claim are verified. `TOKEN_ACTIVE_KEY_ID` defaults to `v1`; old signing keys can be retained under `security.token.previous-keys` during rotation. See the profile files for optional limits, timeouts, tracing, and route settings.

Email alerts are disabled by default and require no SMTP service. To enable them, set `ALERTING_ENABLED=true`, `ALERTING_EMAIL_ENABLED=true`, and `SPRING_MAIL_HOST` to your SMTP server. Set `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, and SMTP authentication/TLS properties as required by that server. The application fails startup with a clear message if email alerts are enabled without a mail sender. The mail health check runs only when email alerts are enabled.

## Run locally

Requires Java 21, Docker Compose, and a shell with `openssl`.

```bash
bash scripts/generate-local-env.sh
docker-compose --env-file deploy/local/.env -f deploy/local/compose.yml up -d --build
curl -fsS http://localhost:8080/actuator/health/readiness
```

The generator writes `deploy/local/.env` once with local-only random credentials. That file is ignored by Git. Compose starts PostgreSQL, Redis, a Flyway migration job, Keycloak, and the API. API port 8080 and Keycloak port 8081 are bound to loopback. Use `--profile replicas` on the Compose command to start a second API instance on port 8082. Use `--profile aws` to add optional LocalStack; the API does not currently call AWS services. The local stack is described in [deployment operations](deploy/OPERATIONS.md).

For a host or IDE launch, first start the local dependencies. Set `SPRING_PROFILES_ACTIVE=local`, use the keys from the ignored `deploy/local/.env`, and set these connection values:

```text
DATABASE_URL=jdbc:postgresql://localhost:55435/transfer_aggregator
DATABASE_USER=transfer_aggregator
REDIS_HOST=localhost
REDIS_PORT=56379
OIDC_ISSUER_URI=http://localhost:8081/realms/transfer-local
OIDC_JWK_SET_URI=http://localhost:8081/realms/transfer-local/protocol/openid-connect/certs
OIDC_AUDIENCE=transfer-api
SPRING_FLYWAY_ENABLED=false
```

`DATABASE_PASSWORD`, `REDIS_PASSWORD`, `TOKEN_SECRET`, and `WORKFLOW_ENCRYPTION_KEY` must also be set from that local file. The migration job applies the schema before the API starts. A missing or retired `TOKEN_SECRET` intentionally fails startup with a validation error.

The local realm has a `transfer-local-cli` client. Create a local user in [Keycloak](http://localhost:8081/admin/), assign a realm role such as `CUSTOMER`, and obtain an access token through the realm's OIDC endpoint. Requests need a valid token with the configured audience, role, and enabled tenant claim. The realm and password grant are for loopback development only.

## API and behavior

The [OpenAPI contract](src/main/resources/openapi.yaml) describes request and response bodies. The service exposes `/api/v1/transfers/search`, polling, booking, cancellation, booking changes, route estimates, and authenticated operation and admin endpoints. Swagger UI is at `/swagger-ui.html` for support and operator roles.

Search fans out to enabled suppliers with per-supplier timeouts, circuit breakers, a bulkhead, retries for safe reads, and partial results when a supplier fails. Search state is an expiring Redis cache. Booking and supported change commits require `Idempotency-Key`; PostgreSQL claims each tenant-scoped key before a supplier write. An ambiguous write becomes `UNKNOWN_OUTCOME` for reconciliation. Cancellation is stored before the API returns `202`; leased workers process durable jobs and a PostgreSQL dead-letter queue retains failures. Operator actions are audited.

The API validates OIDC issuer and audience, extracts tenant and role claims, and rejects tenant header mismatches. `CUSTOMER` can make business requests; `SUPPORT` can inspect authorized read and admin views; `OPERATOR` can perform authorized admin actions. Health probes are public. Metrics require an operator token; API documentation requires a support or operator token.

Optional OSRM route estimates need `ROUTING_ENABLED=true` and `ROUTING_BASE_URL` for a private OSRM endpoint. The public OSRM demonstration server is rejected. Route results provide OpenStreetMap / OSRM attribution.

## Verification and operations

```bash
./mvnw -B -ntp test
ruby scripts/validate_openapi.rb
kubectl kustomize deploy/k8s/overlays/production
kubectl kustomize deploy/k8s/migrations/production
```

Run Maven with Java 21. The full `-Pstatic-analysis clean verify` gate also runs PostgreSQL and Redis integration tests when `TRANSFER_TEST_POSTGRES_URL` and the related test connection variables are supplied. GitHub CI runs the gate, builds application and migration images, and scans source, dependencies, and image operating systems. CI does not publish or deploy images.

The [deployment runbook](deploy/OPERATIONS.md) covers local startup, immutable production images, migrations, recovery, and supplier onboarding. The [observability runbook](observability/OPERATIONS.md) covers dashboards, traces, alert response, unknown outcomes, and DLQ handling. Production still needs an approved live supplier contract and a validated release environment before customer traffic.
