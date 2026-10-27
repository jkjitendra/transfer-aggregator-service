# Airport Transfer Aggregator Microservice

An airport transfer aggregation service built as part of Arcube's ancillary platform assignment. It demonstrates supplier adapters, resilience, and API design, but is **not ready for production traffic**.

---

## Table of Contents

1. [About the Project](#1-about-the-project)
2. [Tech Stack &amp; Dependencies](#2-tech-stack--dependencies)
3. [Architecture](#3-architecture)
4. [Features](#4-features)
5. [Getting Started](#5-getting-started)
6. [Testing with Postman &amp; Swagger](#6-testing-with-postman--swagger)
7. [Configuration Guide](#7-configuration-guide-applicationyml)
8. [API Documentation](#8-api-documentation)
9. [Design Decisions](#9-design-decisions)
10. [What's Extra vs Original Assignment](#10-whats-extra-vs-original-assignment)

---

## 1. About the Project

This service acts as a **central aggregator** for airport transfer suppliers, enabling airlines to offer ground transportation (airport ↔ hotel/home) as an ancillary product through a single, unified API.

### Key Flows

| Flow | Description |
|------|-------------|
| **Search** | Query multiple suppliers in parallel, return aggregated offers |
| **Poll** | Retrieve async results with filtering, sorting, and pagination |
| **Pricing** | Calculate total price with selected amenities |
| **Book** | Atomically claim an idempotency key in PostgreSQL before a supplier write outside demo; ambiguous outcomes enter review |
| **Cancel** | Persist a cancellation job before acknowledgement, then process it with PostgreSQL leases and a durable DLQ outside demo |
| **Cancel Status** | Check the status of a cancellation request |
| **Booking Change** | Search for alternatives and commit changes to existing bookings |

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          CLIENT REQUEST FLOW                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   Search ──► Poll (filter/sort) ──► Pricing ──► Book                    │
│                                                   │                     │
│                                                   ▼                     │
│                                              Cancel / Change            │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Tech Stack & Dependencies

| Category | Technology |
|----------|------------|
| **Language** | Java 21 (LTS) |
| **Framework** | Spring Boot 3.5.x |
| **Build** | Maven (wrapper included) |
| **Concurrency** | Virtual Threads (Project Loom) |
| **HTTP Client** | Spring WebFlux WebClient (non-blocking) |
| **Persistence** | PostgreSQL/Flyway for write workflows; Redis for expiring search state and distributed rate limits; Caffeine for demo-only state |
| **Resilience** | Resilience4j (Circuit Breaker) + Custom (Rate Limiter, Bulkhead, Retry) |
| **Observability** | Micrometer + Prometheus metrics |
| **API Docs** | OpenAPI 3.0 + Swagger UI |
| **Validation** | Jakarta Bean Validation |

### Observability Features

- **Metrics**: Exposed via `/actuator/prometheus`
- **Health Probes**: Liveness/Readiness at `/actuator/health`
- **Request Correlation**: `X-Request-Id` on inbound requests and MDC logging; distributed trace propagation is not implemented
- **Structured Logging**: JSON-compatible logs with `requestId` in MDC

---

## 3. Architecture

This service is built using **Hexagonal Architecture (Ports and Adapters)** to ensure domain logic remains isolated from external concerns.

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              API Layer (Controllers)                            │
│     ┌──────────────┐ ┌───────────────┐ ┌─────────────────┐ ┌───────────────┐    │
│     │ Transfer     │ │ Pricing       │ │ BookingChange   │ │ Alert         │    │
│     │ Controller   │ │ Controller    │ │ Controller      │ │ Controller    │    │
│     └──────┬───────┘ └───────┬───────┘ └────────┬────────┘ └───────┬───────┘    │
└────────────┼─────────────────┼──────────────────┼──────────────────┼────────────┘
             │                 │                  │                  │
┌────────────▼─────────────────▼──────────────────▼──────────────────▼────────────┐
│                             Service Layer                                       │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────────────────────────┐ │
│  │ TransferSearch  │  │ PricingService  │  │ TransferBookingChangeService     │ │
│  │ Service         │  │                 │  │                                  │ │
│  ├─────────────────┤  ├─────────────────┤  ├──────────────────────────────────┤ │
│  │ SearchPolling   │  │ OfferFilter     │  │ TransferCancellationService      │ │
│  │ Service         │  │ Service         │  │                                  │ │
│  ├─────────────────┤  └─────────────────┘  ├──────────────────────────────────┤ │
│  │ TransferBooking │                       │ AlertingService                  │ │
│  │ Service         │                       │                                  │ │
│  └────────┬────────┘                       └──────────────────────────────────┘ │
└───────────┼─────────────────────────────────────────────────────────────────────┘
            │
┌───────────▼─────────────────────────────────────────────────────────────────────┐
│                           Resilience Layer                                      │
│  ┌────────────────┐ ┌────────────┐ ┌────────────┐ ┌───────────────────────────┐ │
│  │ CircuitBreaker │ │ Bulkhead   │ │ RateLimiter│ │ RetryHandler              │ │
│  │ (Resilience4j) │ │            │ │            │ │                           │ │
│  └────────────────┘ └────────────┘ └────────────┘ └───────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │ CancellationQueue → CancellationWorker → CancellationDLQ                 │   │
│  └──────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
            │
┌───────────▼─────────────────────────────────────────────────────────────────────┐
│                         Adapter Layer (Suppliers)                               │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐                    │
│  │ MockSupplier    │ │ SlowMockSupplier│ │ SkyRideSupplier │                    │
│  │ (STUB)          │ │ (SLOW_STUB)     │ │ (Mock Premium)  │                    │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘                    │
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │ OfferIdCodec / BookingIdCodec (HMAC-signed stateless tokens)            │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Sequence Diagrams

#### Search Flow

![Search Flow Sequence Diagram](docs/Search%20Flow%20Sequence%20Diagram.png)

#### Poll Flow

![Poll Flow Sequence Diagram](docs/Poll%20Flow%20Sequence%20Diagram.png)

#### Pricing Flow

![Pricing Flow Sequence Diagram](docs/Pricing%20Flow%20Sequence%20Diagram.png)

#### Book Flow (with Idempotency)

![Book Flow Sequence Diagram](docs/Book%20Flow%20Sequence%20Diagram.png)

#### Cancel + Cancel Status Flow

![Cancel Flow Sequence Diagram](docs/Cancel%20Flow%20Sequence%20Diagram.png)

#### Booking Change Flow

![Booking Change Flow Sequence Diagram](docs/Booking%20Change%20Flow%20Sequence%20Diagram.png)

#### Admin Alerts Flow

![Admin Alert Flow Sequence Diagram](docs/Admin%20Alert%20Flow%20Sequence%20Diagram.png)

### Extensibility

Adding a new supplier is straightforward:

1. Implement the `TransferSupplier` interface
2. Create HTTP client/adapter in `adapters/supplier/<name>/`
3. Register as a Spring Bean — `SupplierRegistry` auto-discovers it
4. Add configuration in `application.yml` under `transfer.aggregator.suppliers`

---

## 4. Features

| Feature | Description |
|---------|-------------|
| **Parallel Supplier Search** | Queries all enabled suppliers concurrently with configurable timeouts. Returns partial results if some suppliers time out. |
| **Signed Stateless Tokens** | `offerId` and `bookingId` are Base64url-encoded, HMAC-signed tokens containing supplier context. Their contents are readable after decoding and the token alone does not authorize an operation. |
| **Idempotency** | Booking and supported change commits require `Idempotency-Key` outside demo. A PostgreSQL claim is scoped by tenant, operation type, and key hash; different request fingerprints conflict. Demo retains a local booking cache. |
| **Polling with Filtering/Sorting/Pagination** | Poll endpoint supports price range, vehicle type/class/category, capacity, amenities, provider ratings, cancellation policy, and duration filters. Sorting by price, rating, or duration. **Pagination is applied on the aggregated response from all suppliers.** |
| **Pricing with Amenities** | Calculate total price including optional amenities (baby seats, WiFi, etc.). Get available amenities per offer. |
| **Booking Change Workflow** | Search and commit through suppliers implementing `ReservationChangeSupplier`; the default mock implements it. Other suppliers return an unsupported result. |
| **Circuit Breaker** | Per-supplier circuit breaker (Resilience4j) with configurable thresholds. Fails fast for unhealthy suppliers. |
| **Rate Limiting** | Redis-backed search and poll counters across replicas outside demo; local counters in demo/test. The poll service consumes a permit. |
| **Bulkhead** | Limits concurrent supplier calls to prevent resource exhaustion. |
| **Retry with Backoff** | Automatic retries for transient failures with exponential backoff. |
| **Async Cancellation Queue** | Cancellation jobs are persisted before acknowledgement and claimed in bounded leased batches outside demo. Automatic retry is limited to suppliers with a verified idempotent cancellation contract. |
| **Dead Letter Queue (DLQ)** | Exhausted safe retries enter PostgreSQL-backed dead letters. Operator replay/resolve requires an evidence reference and writes an audit event. |
| **Admin Alerting Endpoints** | Support and operator roles can read alert counters; only operators can trigger checks. Simulate/clear routes are registered only in the `demo` profile. Counters still do not operate on real DLQ entries. |
| **Multi-Tenancy** | The validated JWT tenant claim selects an explicitly configured, enabled tenant. A conflicting `X-Tenant-Id` is rejected. Supplier filtering, default currency, and per-supplier result limits are enforced. |
| **Health & Metrics** | Health probes at `/actuator/health`. Prometheus metrics for monitoring. |

---

## 5. Getting Started

### Prerequisites

- **Java 21+** — Verify with `java -version`
- **Maven** — Wrapper included (`./mvnw`)
- **Redis** — Required for search polling and distributed rate limits outside demo
- **PostgreSQL** — Required outside demo; Flyway applies the workflow schema at startup

### Clone & Setup

```bash
git clone https://github.com/jkjitendra/transfer-aggregator-service.git
cd transfer-aggregator-service
```

### Build

```bash
./mvnw clean install
```

### Run Locally

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

The demo profile starts on **port 8080** with mock suppliers and local-only API access. Without the demo profile, startup requires OIDC issuer/audience settings and a 32+ byte token secret. Do not expose the demo profile to external traffic.

### Run Tests

```bash
./mvnw test
```

### Useful URLs

| URL | Description |
|-----|-------------|
| http://localhost:8080/swagger-ui.html | Swagger UI (API documentation) |
| http://localhost:8080/api-docs | OpenAPI JSON spec |
| http://localhost:8080/actuator/health | Health check endpoint |
| http://localhost:8080/actuator/prometheus | Prometheus metrics |

---

## 6. Testing with Postman & Swagger

### Postman Collection

A complete Postman collection is included in the repository:

[AgrregatorService Postman Collection](postman/AgrregatorService.postman_collection.json)

**How to use:**

1. Open Postman and click **Import**
2. Select the collection file from `postman/AgrregatorService.postman_collection.json`
3. Start the application (`./mvnw spring-boot:run`)
4. Run requests in order:
   - **Search** → Get `searchId`
   - **Poll** → Get offers with `offerId`
   - **Pricing** → Calculate price with amenities
   - **Book** → Create booking, get `bookingId`
   - **Cancel** → Cancel booking
   - **Cancel Status** → Check cancellation result

### Swagger UI

Once the application is running, access the interactive API documentation at:

**http://localhost:8080/swagger-ui.html**

Swagger loads the OpenAPI spec from `/openapi.yaml` and provides an interactive interface to test all endpoints.

---

## 7. Configuration Guide (application.yml)

### Core Aggregator Settings

| Key | Default | Description | Effect When Changed |
|-----|---------|-------------|---------------------|
| `transfer.aggregator.global-timeout-seconds` | `10` | Max time for all operations | Increase for slow suppliers |

### Supplier Configuration

| Key | Default | Description |
|-----|---------|-------------|
| `transfer.aggregator.suppliers.stub.enabled` | `true` | Enable mock supplier (fast, returns immediately) |
| `transfer.aggregator.suppliers.slow-stub.enabled` | `true` | Enable slow mock supplier (simulates async polling) |
| `transfer.aggregator.suppliers.skyride.enabled` | `true` | Enable SkyRide mock supplier (premium vehicles) |

All three supplier adapters generate synthetic offers and reservations. The `prod` profile disables them. Outside `demo` and `test`, startup fails unless a verified `LiveTransferSupplier` is configured. `REQUIRE_LIVE_SUPPLIER=false` is an explicit local integration-test override, not a deployment setting. Route estimates alone do not provide availability, pricing, booking, or cancellation.

### Optional Open-Source Route Estimates

`GET /api/v1/routes/estimate` returns driving distance and duration from [OSRM](https://project-osrm.org/docs/v26.5.0/http). It is read-only and can support a future journey visualization. The `demo` profile uses the public OSRM server and limits calls to one per second per process. That server is for low-volume demonstrations and has no availability guarantee. Production should run OSRM on its own infrastructure and set `ROUTING_ENABLED=true` and `ROUTING_BASE_URL` to its HTTPS or local endpoint. The default non-demo endpoint is `http://localhost:5000`, with routing disabled until configured.

Example: `/api/v1/routes/estimate?pickupLatitude=52.517037&pickupLongitude=13.38886&dropoffLatitude=52.529407&dropoffLongitude=13.397634`. The response includes the required OpenStreetMap / OSRM attribution. Do not send customer location data to the public demo endpoint.

### Resilience Configuration

| Key | Default | Description |
|-----|---------|-------------|
| `transfer.aggregator.resilience.max-concurrent-calls` | `50` | Max concurrent supplier calls (bulkhead) |
| `transfer.aggregator.resilience.search-rate-limit-per-minute` | `80` | Per-tenant, per-supplier Redis limit outside demo; process-local in demo |
| `transfer.aggregator.resilience.poll-rate-limit-per-minute` | `25` | Poll limit per search ID; Redis-backed outside demo |
| `transfer.aggregator.resilience.supplier-timeouts.<CODE>.*-seconds` | Operation-specific supplier deadline | Configure search, booking, change, and cancellation timeouts per supplier |

Provisional local verification targets: search p95 under 2 seconds with 20 concurrent requests against simulated suppliers, no more than 50 simultaneous supplier calls, and no duplicate supplier write for the same idempotency key. These are engineering targets for the local test stack, not a claim about a live transfer provider or the public route demo.

Search fan-out uses virtual threads and a shared request deadline. Timed-out supplier calls are interrupted and reported as partial results. Circuit-breaker failures are reported as unavailable, never as successful empty inventory. Booking and change writes are not retried on transport failures without verified upstream idempotency. All current booking suppliers are simulated; no live transfer reservation is created.

### Circuit Breaker Configuration (Resilience4j)

| Key | Default | Description |
|-----|---------|-------------|
| `resilience4j.circuitbreaker.configs.default.slidingWindowSize` | `10` | Number of calls in sliding window |
| `resilience4j.circuitbreaker.configs.default.failureRateThreshold` | `50` | Failure rate % to open circuit |
| `resilience4j.circuitbreaker.configs.default.waitDurationInOpenState` | `30s` | Time in OPEN before HALF_OPEN |
| `resilience4j.circuitbreaker.configs.default.permittedNumberOfCallsInHalfOpenState` | `3` | Test calls in HALF_OPEN |

### Multi-Tenancy Configuration

| Key | Description |
|-----|-------------|
| `transfer.aggregator.tenants.<tenant-id>.name` | Tenant name |
| `transfer.aggregator.tenants.<tenant-id>.enabled` | Declared but not currently enforced |
| `transfer.aggregator.tenants.<tenant-id>.enabled-suppliers` | Allowed suppliers for tenant |
| `transfer.aggregator.tenants.<tenant-id>.default-currency` | Declared but not currently applied to searches |
| `transfer.aggregator.tenants.<tenant-id>.max-results-per-supplier` | Declared but not currently enforced |

### Alerting Configuration

| Key | Default | Description |
|-----|---------|-------------|
| `transfer.aggregator.alerting.enabled` | `false` | Enable alerting system |
| `transfer.aggregator.alerting.check-interval-seconds` | `60` | Alert check frequency |
| `transfer.aggregator.alerting.dlq-warning-threshold` | `10` | DLQ size to trigger warning |
| `transfer.aggregator.alerting.dlq-critical-threshold` | `50` | DLQ size to trigger critical alert |
| `transfer.aggregator.alerting.error-rate-warning-threshold` | `5` | Errors/min to trigger warning |
| `transfer.aggregator.alerting.error-rate-critical-threshold` | `20` | Errors/min to trigger critical |
| `transfer.aggregator.alerting.email.enabled` | `false` | Enable email alerts |
| `transfer.aggregator.alerting.email.recipients` | `operations@arcube.com` | Alert email recipients |

### Redis Configuration (Standalone Mode)

| Key | Default | Description |
|-----|---------|-------------|
| `spring.data.redis.host` | `localhost` | Redis host (standalone mode) |
| `spring.data.redis.port` | `6379` | Redis port (standalone mode) |
| `spring.data.redis.password` | `(empty)` | Redis password |
| `spring.data.redis.timeout` | `2000ms` | Connection timeout |

### Durable Workflow Configuration

Outside the `demo` and `test` profiles, the service requires PostgreSQL and a base64-encoded 32-byte `WORKFLOW_ENCRYPTION_KEY`. Flyway creates `operations`, `idempotency_records`, `cancellation_jobs`, `dead_letters`, and `audit_events`. Sensitive response and reservation payloads are encrypted before storage. Backups and PostgreSQL volumes must also be encrypted and access controlled. Retain the encryption key for as long as persisted records or backups need to be readable.

| Variable | Purpose |
|----------|---------|
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | PostgreSQL JDBC connection |
| `WORKFLOW_ENCRYPTION_KEY` | Base64-encoded 32-byte AES-GCM key; required outside demo/test |
| `IDEMPOTENCY_RETENTION_DAYS` | Same-key replay window (default 7 days) |
| `OPERATION_RETENTION_DAYS` | Terminal operation and audit retention (default 90 days) |
| `CANCELLATION_MAX_ATTEMPTS` | Maximum verified-safe cancellation attempts (default 3) |
| `CANCELLATION_LEASE_SECONDS` | Worker claim lease (default 30 seconds) |

Booking and supported change commits require `Idempotency-Key` outside demo. A repeated key with the same request returns the stored response or `UNKNOWN_OUTCOME` while the first attempt is unresolved; a different request returns `409`. A supplier timeout is not retried automatically when its write idempotency contract is unverified. Use `GET /api/v1/operations/{operationId}` to inspect status. Cancellation returns `202` after its job is stored; status and DLQ state survive a service restart. Search results remain a 10-minute Redis cache; missing IDs return `404`, recently expired IDs return `410`, and Redis failure returns `503`.

### Security Configuration

| Key | Description |
|-----|-------------|
| `security.api.enabled` | Require OIDC bearer tokens by default; may be disabled only in `demo` or `test` profiles. |
| `security.api.issuer-uri` / `security.api.audience` | Trusted OIDC issuer and required JWT audience. Production issuer must use HTTPS. |
| `security.api.roles-claim` / `security.api.tenant-claim` | JWT claims for `CUSTOMER`, `SUPPORT`, `OPERATOR` roles and tenant ID. |
| `security.api.allowed-origins` | Explicit browser origins. Wildcards are rejected. |
| `security.token.secret` | Required 32+ byte HMAC key. Missing, short, or retired demo secrets fail startup. |
| `security.token.active-key-id` / `security.token.previous-keys` | Key ID for newly issued IDs and old keys retained only during rotation. |

Authenticated business requests require a JWT with the configured issuer, audience, role, and an enabled `tenant_id` claim. `X-Tenant-Id` is optional and must match the claim if supplied. Customer and operator roles can write business requests; support can read them. Support and operator can read admin alert endpoints; only operators can trigger alert checks or access Prometheus metrics. API docs require support or operator. The health endpoint is public.

### Environment Variables

| Variable | Maps To | Required |
|----------|---------|----------|
| `TOKEN_SECRET` | `security.token.secret` | Outside demo/test |
| `TOKEN_ACTIVE_KEY_ID` | `security.token.active-key-id` | No |
| `OIDC_ISSUER_URI` / `OIDC_AUDIENCE` | `security.api.issuer-uri` / `security.api.audience` | Outside demo/test |
| `OIDC_TENANT_CLAIM` / `OIDC_ROLES_CLAIM` | JWT claim names | No |
| `ROUTING_ENABLED` / `ROUTING_BASE_URL` / `ROUTING_TIMEOUT` | Optional OSRM route estimates | No |
| `REQUIRE_LIVE_SUPPLIER` | Startup guard for a verified booking supplier outside demo/test | Defaults to `true` |
| `CORS_ALLOWED_ORIGINS` | `security.api.allowed-origins` | For browser clients |
| `REDIS_HOST` | `spring.data.redis.host` | For distributed caching |
| `ALERTING_ENABLED` | `transfer.aggregator.alerting.enabled` | No |

---

## 8. API Documentation

### Quick Endpoint Reference

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/v1/transfers/search` | Search for transfer offers |
| `GET` | `/api/v1/transfers/search/{searchId}/poll` | Poll results with filters |
| `POST` | `/api/v1/pricing` | Calculate price with amenities |
| `GET` | `/api/v1/pricing` | Get price via query params |
| `GET` | `/api/v1/pricing/{offerId}/amenities` | Get available amenities |
| `POST` | `/api/v1/transfers/book` | Book a transfer |
| `DELETE` | `/api/v1/transfers/bookings/{bookingId}` | Cancel a booking |
| `GET` | `/api/v1/transfers/bookings/{bookingId}/cancel-status` | Get cancel status |
| `POST` | `/api/v1/transfers/bookings/{bookingId}/search-changes` | Search booking alternatives |
| `POST` | `/api/v1/transfers/bookings/{bookingId}/commit-change` | Commit booking change |
| `GET` | `/api/v1/admin/alerts/dlq` | Get real PostgreSQL DLQ size outside demo |
| `GET` | `/api/v1/operations/{operationId}` | Get tenant-owned durable operation status outside demo |
| `GET` | `/api/v1/admin/operations/review` | List ambiguous write operations outside demo |
| `POST` | `/api/v1/admin/operations/{operationId}/review` | Record a manual review decision and evidence reference |
| `GET` | `/api/v1/admin/cancellations/dlq` | List real dead letters outside demo |
| `POST` | `/api/v1/admin/cancellations/dlq/{jobId}/replay` | Replay verified-safe cancellation after operator review |
| `POST` | `/api/v1/admin/cancellations/dlq/{jobId}/resolve` | Record manual resolution and audit event |
| `DELETE` | `/api/v1/admin/alerts/dlq` | Demo profile only: clear simulated DLQ metric counter |
| `GET` | `/api/v1/admin/alerts/errors` | Get error metrics |
| `POST` | `/api/v1/admin/alerts/dlq/simulate` | Demo profile only: simulate DLQ items |
| `POST` | `/api/v1/admin/alerts/check` | Trigger alert check |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

---

### Endpoint Details

The `curl` examples below assume the local `demo` profile. For authenticated deployments, include `Authorization: Bearer <OIDC access token>`; the token must contain an enabled tenant claim for business requests.

#### POST /api/v1/transfers/search

Search for transfer offers across all enabled suppliers.

**Headers:**
- `X-Request-Id` (optional): Request tracing ID

**Request Body:**
```json
{
  "pickupLocation": {
    "address": "433 Park Ave, New York, NY"
  },
  "dropoffLocation": {
    "iataCode": "JFK"
  },
  "pickupDateTime": "2024-08-16T15:30:00",
  "numPassengers": 2,
  "numBags": 2,
  "currency": "USD",
  "mode": "ONE_WAY"
}
```

**Response:** `200 OK`
```json
{
  "searchId": "abc123",
  "offers": [...],
  "incomplete": false,
  "supplierStatuses": {
    "STUB": {"status": "SUCCESS", "resultsCount": 5},
    "SKYRIDE": {"status": "TIMEOUT", "resultsCount": 0}
  }
}
```

**Status Codes:** `200`, `400`, `429`, `503`

**curl Example:**
```bash
curl -X POST http://localhost:8080/api/v1/transfers/search \
  -H "Content-Type: application/json" \
  -H "X-Request-Id: req-001" \
  -d '{
    "pickupLocation": {"address": "Times Square, NYC"},
    "dropoffLocation": {"iataCode": "JFK"},
    "pickupDateTime": "2024-08-16T10:00:00",
    "numPassengers": 2,
    "numBags": 1
  }'
```

---

#### GET /api/v1/transfers/search/{searchId}/poll

Poll for search results with filtering, sorting, and pagination.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | 0 | Page number (0-indexed) |
| `size` | int | 20 | Page size (max 200) |
| `sortBy` | string | PRICE | Sort field: PRICE, RATING, DURATION |
| `sortDir` | string | ASC | Direction: ASC, DESC |
| `minPrice` | number | - | Minimum price filter |
| `maxPrice` | number | - | Maximum price filter |
| `vehicleTypes` | array | - | Filter by vehicle types |
| `vehicleClasses` | array | - | Filter by vehicle classes |
| `amenities` | array | - | Required amenities |
| `freeCancellationOnly` | boolean | - | Only free cancellation offers |
| `minRating` | number | - | Minimum provider rating (0-5) |
| `maxDuration` | int | - | Max trip duration in minutes |

**curl Example:**
```bash
curl "http://localhost:8080/api/v1/transfers/search/abc123/poll?page=0&size=10&sortBy=PRICE&maxPrice=100&freeCancellationOnly=true"
```

---

#### POST /api/v1/pricing

Calculate total price for an offer with selected amenities.

**Request Body:**
```json
{
  "searchId": "abc123",
  "offerId": "eyJzdXAiOiJTVFVCIi...",
  "amenities": ["baby_seats", "wifi"]
}
```

**Response:**
```json
{
  "searchId": "abc123",
  "offerId": "eyJzdXAiOiJTVFVCIi...",
  "basePrice": {"value": 45.00, "currency": "USD"},
  "selectedAmenities": [
    {"key": "baby_seats", "name": "Baby Seats", "price": {"value": 10.00, "currency": "USD"}}
  ],
  "totalPrice": {"value": 55.00, "currency": "USD"}
}
```

**Status Codes:** `200`, `400`, `404`, `410` (expired), `429`

---

#### GET /api/v1/pricing/{offerId}/amenities

Get list of available amenities for an offer.

**curl Example:**
```bash
curl "http://localhost:8080/api/v1/pricing/eyJzdXAiOiJTVFVCIi.../amenities"
```

---

#### POST /api/v1/transfers/book

Book a transfer using an offer ID.

**Headers:**
- `Idempotency-Key`: Required outside demo; atomically claims a seven-day tenant-scoped booking operation

**Request Body:**
```json
{
  "offerId": "eyJzdXAiOiJTVFVCIi...",
  "passenger": {
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phoneNumber": "+1234567890",
    "countryCode": "US"
  },
  "flight": {
    "airline": "AA",
    "flightNumber": "123"
  }
}
```

**Response:**
```json
{
  "bookingId": "eyJib29raW5nSWQiOi...",
  "status": "CONFIRMED",
  "confirmationNumber": "STUB-BK-12345",
  "totalPrice": {"value": 45.00, "currency": "USD"},
  "pickupInstructions": "Driver will meet you at Terminal 4"
}
```

**Status Codes:** `200`, `400`, `404`, `409` (duplicate/price changed), `410` (expired), `429`

**curl Example:**
```bash
curl -X POST http://localhost:8080/api/v1/transfers/book \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: book-$(uuidgen)" \
  -d '{
    "offerId": "eyJzdXAiOiJTVFVCIi...",
    "passenger": {
      "firstName": "John",
      "lastName": "Doe",
      "email": "john@example.com",
      "phoneNumber": "+1234567890",
      "countryCode": "US"
    }
  }'
```

---

#### DELETE /api/v1/transfers/bookings/{bookingId}

Cancel a booking (async processing).

**Response:**
```json
{
  "bookingId": "eyJib29raW5nSWQiOi...",
  "status": "PENDING",
  "message": "Cancellation request submitted"
}
```

**Status Codes:** `200`, `400`, `404`, `409` (already cancelled)

---

#### GET /api/v1/transfers/bookings/{bookingId}/cancel-status

Get the status of a cancellation.

**Response:**
```json
{
  "bookingId": "eyJib29raW5nSWQiOi...",
  "status": "CANCELLED",
  "refundAmount": {"value": 45.00, "currency": "USD"},
  "refundedAt": "2024-08-16T12:00:00Z"
}
```

---

#### POST /api/v1/transfers/bookings/{bookingId}/search-changes

Search for alternative offers to change an existing booking.

**Request Body:**
```json
{
  "pickupDateTime": "2024-08-17T10:00:00",
  "numPassengers": 3
}
```

**Response:** Same as search response with alternative offers.

---

#### POST /api/v1/transfers/bookings/{bookingId}/commit-change

Commit a booking change if the supplier supports its native change operation. The current mock supplier supports this flow for local testing.

**Headers:**
- `Idempotency-Key`: Required outside demo for a supplier-supported change; conflicting reuse returns `409`

**Request Body:**
```json
{
  "resultId": "new-offer-id-from-search-changes"
}
```

**Response:**
```json
{
  "oldBookingId": "eyJib29raW5nSWQiOi...",
  "newBookingId": "eyJuZXdCb29raW5nSWQ...",
  "status": "COMPLETED",
  "newBooking": {...}
}
```

---

#### Admin Alert Endpoints

```bash
# Get DLQ status
curl http://localhost:8080/api/v1/admin/alerts/dlq

# Get error metrics
curl http://localhost:8080/api/v1/admin/alerts/errors

# Simulate DLQ items (demo profile only)
curl -X POST "http://localhost:8080/api/v1/admin/alerts/dlq/simulate?count=5"

# Clear simulated DLQ metric counter (demo profile only; does not resolve real tasks)
curl -X DELETE http://localhost:8080/api/v1/admin/alerts/dlq

# Trigger alert check
curl -X POST http://localhost:8080/api/v1/admin/alerts/check
```

---

## 9. Design Decisions

### 1. Stateless Signed IDs (HMAC)

**Problem:** Storing search context and offer details for every search creates state management complexity and database overhead.

**Current implementation:** HMAC-SHA256 signs Base64url payloads with a key ID and tenant binding. Offer IDs contain supplier/search/result IDs and issue/expiry timestamps. Booking IDs contain supplier/reservation/confirmation IDs and no expiry. HMAC provides integrity, not confidentiality; neither token is encrypted.

**Benefits:**
- No database lookup for signature validation
- Makes forged IDs difficult without the signing key
- Self-contained context for booking
- Detects payload tampering; authorization must be checked separately

---

### 2. Adapter Pattern for Suppliers

**Problem:** Each supplier has a different API contract, authentication, and response format.

**Solution:** Hexagonal architecture with:
- `TransferSupplier` interface as the port
- Supplier-specific adapters implementing the interface
- `SupplierRegistry` for auto-discovery

**Benefits:**
- Adding new suppliers requires only implementing the interface
- Supplier logic is isolated and testable
- Easy to swap implementations (mock/real)

---

### 3. Polling Model vs Synchronous Aggregation

**Problem:** Some suppliers return results asynchronously, so an initial response may be incomplete.

**Solution:** Two-phase approach:
1. **Search**: Initiates parallel queries, returns immediately available results
2. **Poll**: Client polls for updates until `incomplete: false`

**Benefits:**
- Better UX (show results as they arrive)
- Handles suppliers with different response times
- Client controls polling frequency

---

### 4. Resilience Approach

**Circuit Breaker (Resilience4j):**
- Per-supplier circuit breakers
- Prevents cascading failures
- Automatic recovery (HALF_OPEN → CLOSED)

**Rate Limiter:**
- Prevents abuse (80 searches/min, 25 polls/min per search)
- Sliding window approximation with Caffeine cache

**Bulkhead:**
- Limits concurrent supplier calls (50)
- Prevents thread pool exhaustion

**Retry Handler:**
- Exponential backoff (100ms → 200ms → 400ms)
- Only retries transient failures (timeouts, 5xx)

---

### 5. Cancellation Queue + DLQ

**Problem:** Cancellations can fail due to supplier issues, but must eventually succeed.

**Solution:**
1. **CancellationQueue**: Async processing with retries
2. **CancellationWorker**: Processes and immediately requeues failed tasks in its loop; delayed backoff is not implemented here
3. **CancellationDLQ**: Failed cancellations (after max retries) go to DLQ
4. **AlertController**: Admin alert reads use the real PostgreSQL DLQ count outside demo; simulator routes exist only in demo

**Benefits:**
- Non-blocking cancellation requests
- Automatic retry for transient failures
- In-process visibility into failed cancellations through the internal DLQ component
- The actual DLQ has an internal `resolve` method, but no authenticated replay/resolve API

---

### 6. Multi-Tenancy

**Problem:** Arcube serves multiple airlines, and each airline has different requirements:
- Tenant A allows the standard mock supplier
- Airline B prefers European suppliers with EUR pricing
- Premium partners want more results and access to all suppliers

**Current implementation:** The validated JWT selects an enabled tenant. Search filters suppliers using `enabled-suppliers`, applies the tenant's default currency when none is requested, and limits results per supplier. Unknown or disabled tenants and conflicting `X-Tenant-Id` headers are rejected.

**Example:**
```yaml
tenants:
  airline-a:
    enabled-suppliers: [STUB]
    default-currency: USD
  airline-b:
    enabled-suppliers: [SKYRIDE, SLOW_STUB]
    default-currency: EUR
```

Tenant authentication, ownership checks, and enforcement of the remaining configured fields are planned before production use.

---

### 7. Admin Alerting

**Problem:** In production, operations teams need visibility into system health:
- How many cancellations are stuck in DLQ?
- Are error rates spiking for a specific operation?
- Is the system healthy enough to handle traffic?

**Current implementation:** Alert endpoints provide:
- **Simulated DLQ counter** — View/clear a counter separate from real cancellation DLQ items
- **Error metrics** — Aggregate error counts by operation type
- **Manual alert triggers** — Force an alert check on-demand
- **Configurable thresholds** — Warning and critical levels for DLQ size and error rates

**Current value:** Outside demo, alert thresholds read the PostgreSQL-backed cancellation DLQ. Role-based access is enforced, and operator replay/resolve actions write audit events. Alert delivery and operational runbooks still require deployment validation.

---

## 10. What's Extra vs Original Assignment

### Original Requirements (from Arcube Assignment)

| Requirement | Status |
|-------------|--------|
| Design and implement airport transfer aggregator microservice | ✅ |
| Support search, book, cancel operations | ✅ |
| Clean, extensible architecture for adding suppliers | ✅ |
| Separation of vendor-specific and domain logic | ✅ |
| Proper logging and observability | Partial: request IDs and metrics exist; end-to-end traces do not |
| Distributed tracing support | Not implemented |
| Horizontal and vertical scalability | PostgreSQL coordinates write idempotency and cancellation leases; Redis shares search state and rate counters. The slow mock's local state is demo/test only. |
| README with build, run, test instructions | ✅ |
| Example API calls (Postman collection) | ✅ |

### Additional Capabilities Implemented

| Feature | Description |
|---------|-------------|
| **Polling with Filtering/Sorting/Pagination** | Rich query support for poll endpoint (price, vehicle, amenities, rating, duration filters) |
| **Pricing Endpoint** | Dedicated endpoint for calculating total price with amenities |
| **Amenities API** | Get available amenities per offer |
| **Booking Change Workflow** | Search alternatives and commit changes to existing bookings |
| **Idempotency Support** | PostgreSQL atomic claims for booking and supported change commits outside demo; local booking cache in demo |
| **Multi-Tenancy** | JWT-bound tenant controls supplier filtering, default currency, result limits, and signed IDs |
| **Circuit Breaker** | Resilience4j integration with per-supplier configuration |
| **Rate Limiting** | Process-local per-supplier search limiter; poll limiter exists but is not wired to the poll endpoint |
| **Bulkhead Pattern** | Concurrent call limiting |
| **Retry with Backoff** | Automatic retry for transient failures |
| **Async Cancellation Queue** | Non-blocking cancellation with retry logic |
| **Dead Letter Queue** | Failed cancellations tracked for manual resolution |
| **Admin Alert Endpoints** | Simulated DLQ counters, error metrics, and manual alert triggers; currently unauthenticated |
| **Email Alerting Configuration** | Configurable thresholds and recipients |
| **Cancel Status Endpoint** | Check cancellation progress |
| **SkyRide Mock Supplier** | Premium vehicle simulation |
| **Slow Mock Supplier** | Async/polling behavior simulation |
| **Redis Integration** | Incomplete search state stored in Redis; no Caffeine fallback in `SearchPollingService` |
| **OpenAPI 3.0 Spec** | Complete API documentation in `openapi.yaml` |
| **Virtual Threads** | Java 21 Project Loom for high-throughput I/O |
| **Prometheus Metrics** | Request histograms, error counters, DLQ gauges |

---

## License

This project was developed as part of the Arcube assessment.
