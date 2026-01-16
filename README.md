# Airport Transfer Aggregator Microservice

A production-ready microservice for aggregating airport transfer providers (like Mozio) into a unified API. Built as part of Arcube's ancillary platform for airlines.

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
| **Book** | Create a booking with idempotency support |
| **Cancel** | Cancel booking with async queue and DLQ for failed cancellations |
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
| **Caching** | Caffeine (in-memory) + Redis (distributed) |
| **Resilience** | Resilience4j (Circuit Breaker) + Custom (Rate Limiter, Bulkhead, Retry) |
| **Observability** | Micrometer + Prometheus metrics |
| **API Docs** | OpenAPI 3.0 + Swagger UI |
| **Validation** | Jakarta Bean Validation |

### Observability Features

- **Metrics**: Exposed via `/actuator/prometheus`
- **Health Probes**: Kubernetes-ready liveness/readiness at `/actuator/health`
- **Request Tracing**: `X-Request-Id` header propagation for distributed tracing
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
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ ┌───────────────┐  │
│  │ MockSupplier    │ │ SlowMockSupplier│ │ SkyRideSupplier │ │ MozioSupplier │  │
│  │ (STUB)          │ │ (SLOW_STUB)     │ │ (Mock Premium)  │ │ (Real API)    │  │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘ └───────────────┘  │
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
| **Signed Stateless Tokens** | `offerId` and `bookingId` are Base64-encoded, HMAC-signed tokens containing supplier context, preventing enumeration attacks and eliminating database lookups for validation. |
| **Idempotency** | Booking and commit-change operations support `Idempotency-Key` header to prevent duplicate transactions. |
| **Polling with Filtering/Sorting/Pagination** | Poll endpoint supports price range, vehicle type/class/category, capacity, amenities, provider ratings, cancellation policy, and duration filters. Sorting by price, rating, or duration. **Pagination is applied on the aggregated response from all suppliers.** |
| **Pricing with Amenities** | Calculate total price including optional amenities (baby seats, WiFi, etc.). Get available amenities per offer. |
| **Booking Change Workflow** | Search for alternative offers and atomically commit changes (cancel old + book new). |
| **Circuit Breaker** | Per-supplier circuit breaker (Resilience4j) with configurable thresholds. Fails fast for unhealthy suppliers. |
| **Rate Limiting** | Global search rate limit (80/min) and per-searchId poll rate limit (25/min) to prevent abuse. |
| **Bulkhead** | Limits concurrent supplier calls to prevent resource exhaustion. |
| **Retry with Backoff** | Automatic retries for transient failures with exponential backoff. |
| **Async Cancellation Queue** | Cancellations processed asynchronously with retry logic. |
| **Dead Letter Queue (DLQ)** | Failed cancellations (after retries exhausted) go to DLQ for manual review. |
| **Admin Alerting Endpoints** | Monitor DLQ status, error metrics, simulate/clear DLQ, trigger alert checks. |
| **Multi-Tenancy** | Per-tenant configuration for enabled suppliers, default currency, and max results. |
| **Health & Metrics** | Health probes at `/actuator/health`. Prometheus metrics for monitoring. |

---

## 5. Getting Started

### Prerequisites

- **Java 21+** — Verify with `java -version`
- **Maven** — Wrapper included (`./mvnw`)
- **Redis** (optional) — Required for distributed caching in production

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
./mvnw spring-boot:run
```

The service starts on **port 8080** with mock suppliers enabled by default.

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
| `transfer.aggregator.mode` | `stub` | Operation mode | Set to `real` to use actual supplier APIs |
| `transfer.aggregator.global-timeout-seconds` | `10` | Max time for all operations | Increase for slow suppliers |

### Supplier Configuration

| Key | Default | Description |
|-----|---------|-------------|
| `transfer.aggregator.suppliers.stub.enabled` | `true` | Enable mock supplier (fast, returns immediately) |
| `transfer.aggregator.suppliers.slow-stub.enabled` | `true` | Enable slow mock supplier (simulates async polling) |
| `transfer.aggregator.suppliers.skyride.enabled` | `true` | Enable SkyRide mock supplier (premium vehicles) |
| `transfer.aggregator.suppliers.mozio.enabled` | `true` | Enable Mozio integration |
| `transfer.aggregator.suppliers.mozio.base-url` | `https://api-testing.mozio.com` | Mozio API base URL |
| `transfer.aggregator.suppliers.mozio.api-key` | `(empty)` | Mozio API key (required for real calls) |
| `transfer.aggregator.suppliers.mozio.poll-interval-ms` | `2000` | Mozio polling interval |
| `transfer.aggregator.suppliers.mozio.max-poll-attempts` | `5` | Max polling attempts before timeout |
| `transfer.aggregator.suppliers.mozio.search-validity-minutes` | `20` | Mozio search results TTL |

### Resilience Configuration

| Key | Default | Description |
|-----|---------|-------------|
| `transfer.aggregator.resilience.max-concurrent-calls` | `50` | Max concurrent supplier calls (bulkhead) |
| `transfer.aggregator.resilience.search-rate-limit-per-minute` | `80` | Global search rate limit |
| `transfer.aggregator.resilience.poll-rate-limit-per-minute` | `25` | Poll rate limit per searchId |

### Circuit Breaker Configuration (Resilience4j)

| Key | Default | Description |
|-----|---------|-------------|
| `resilience4j.circuitbreaker.configs.default.slidingWindowSize` | `10` | Number of calls in sliding window |
| `resilience4j.circuitbreaker.configs.default.failureRateThreshold` | `50` | Failure rate % to open circuit |
| `resilience4j.circuitbreaker.configs.default.waitDurationInOpenState` | `30s` | Time in OPEN before HALF_OPEN |
| `resilience4j.circuitbreaker.configs.default.permittedNumberOfCallsInHalfOpenState` | `3` | Test calls in HALF_OPEN |
| `resilience4j.circuitbreaker.instances.MOZIO.failureRateThreshold` | `40` | Mozio-specific threshold (stricter) |

### Multi-Tenancy Configuration

| Key | Description |
|-----|-------------|
| `transfer.aggregator.tenants.<tenant-id>.name` | Tenant name |
| `transfer.aggregator.tenants.<tenant-id>.enabled` | Enable/disable tenant |
| `transfer.aggregator.tenants.<tenant-id>.enabled-suppliers` | Allowed suppliers for tenant |
| `transfer.aggregator.tenants.<tenant-id>.default-currency` | Default currency (USD, EUR) |
| `transfer.aggregator.tenants.<tenant-id>.max-results-per-supplier` | Max results per supplier |

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

### Security Configuration

| Key | Description |
|-----|-------------|
| `security.token.secret` | HMAC-SHA256 secret for signing offerId/bookingId tokens. **Change in production!** |

### Environment Variables

| Variable | Maps To | Required |
|----------|---------|----------|
| `AGGREGATOR_MODE` | `transfer.aggregator.mode` | No |
| `MOZIO_ENABLED` | `transfer.aggregator.suppliers.mozio.enabled` | No |
| `MOZIO_API_KEY` | `transfer.aggregator.suppliers.mozio.api-key` | For real Mozio calls |
| `TOKEN_SECRET` | `security.token.secret` | In production |
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
| `GET` | `/api/v1/admin/alerts/dlq` | Get DLQ status |
| `DELETE` | `/api/v1/admin/alerts/dlq` | Clear DLQ |
| `GET` | `/api/v1/admin/alerts/errors` | Get error metrics |
| `POST` | `/api/v1/admin/alerts/dlq/simulate` | Simulate DLQ items |
| `POST` | `/api/v1/admin/alerts/check` | Trigger alert check |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

---

### Endpoint Details

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
    "MOZIO": {"status": "TIMEOUT", "resultsCount": 0}
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
- `Idempotency-Key` (optional): Unique key for idempotent requests

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

Commit a booking change (atomically cancel old + book new).

**Headers:**
- `Idempotency-Key` (optional): For idempotent requests

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

# Simulate DLQ items (testing)
curl -X POST "http://localhost:8080/api/v1/admin/alerts/dlq/simulate?count=5"

# Clear DLQ
curl -X DELETE http://localhost:8080/api/v1/admin/alerts/dlq

# Trigger alert check
curl -X POST http://localhost:8080/api/v1/admin/alerts/check
```

---

## 9. Design Decisions

### 1. Stateless Signed IDs (HMAC)

**Problem:** Storing search context and offer details for every search creates state management complexity and database overhead.

**Solution:** `offerId` and `bookingId` are Base64-encoded, HMAC-SHA256 signed tokens containing:
- Supplier code
- Search ID
- Result ID
- Expiration timestamp
- Issued timestamp

**Benefits:**
- No database lookup for validation
- Prevents ID enumeration attacks
- Self-contained context for booking
- Tamper-proof (signature verification)

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

**Problem:** Suppliers like Mozio return results asynchronously, initial response may be incomplete.

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
2. **CancellationWorker**: Processes queue with exponential backoff
3. **CancellationDLQ**: Failed cancellations (after max retries) go to DLQ
4. **AlertController**: Admin endpoints to monitor/clear DLQ

**Benefits:**
- Non-blocking cancellation requests
- Automatic retry for transient failures
- Visibility into failed cancellations
- Manual resolution possible via DLQ

---

### 6. Multi-Tenancy

**Problem:** Arcube serves multiple airlines, and each airline has different requirements:
- Airline A has a contract with Mozio only
- Airline B prefers European suppliers with EUR pricing
- Premium partners want more results and access to all suppliers

**Solution:** Per-tenant configuration allowing:
- **Enabled suppliers** — Restrict which suppliers are queried per tenant
- **Default currency** — Set preferred currency per tenant
- **Max results** — Limit results per supplier based on partnership tier

**Example:**
```yaml
tenants:
  airline-a:
    enabled-suppliers: [MOZIO]
    default-currency: USD
  airline-b:
    enabled-suppliers: [SKYRIDE, SLOW_STUB]
    default-currency: EUR
```

**Benefits:**
- Single deployment serves multiple clients
- Respects contractual agreements with suppliers
- Flexible pricing and result controls per partnership

---

### 7. Admin Alerting

**Problem:** In production, operations teams need visibility into system health:
- How many cancellations are stuck in DLQ?
- Are error rates spiking for a specific operation?
- Is the system healthy enough to handle traffic?

**Solution:** Admin alert endpoints providing:
- **DLQ monitoring** — View/clear dead letter queue items
- **Error metrics** — Aggregate error counts by operation type
- **Manual alert triggers** — Force an alert check on-demand
- **Configurable thresholds** — Warning and critical levels for DLQ size and error rates

**Benefits:**
- Proactive issue detection before customer impact
- Quick diagnosis during incidents
- Automated email alerts when thresholds exceeded
- Self-service for operations team (no code deployment needed)

---

## 10. What's Extra vs Original Assignment

### Original Requirements (from Arcube Assignment)

| Requirement | Status |
|-------------|--------|
| Design and implement airport transfer aggregator microservice | ✅ |
| Support search, book, cancel operations | ✅ |
| Clean, extensible architecture for adding suppliers | ✅ |
| Separation of vendor-specific and domain logic | ✅ |
| Proper logging and observability | ✅ |
| Distributed tracing support | ✅ |
| Horizontal and vertical scalability | ✅ |
| README with build, run, test instructions | ✅ |
| Example API calls (Postman collection) | ✅ |

### Additional Capabilities Implemented

| Feature | Description |
|---------|-------------|
| **Polling with Filtering/Sorting/Pagination** | Rich query support for poll endpoint (price, vehicle, amenities, rating, duration filters) |
| **Pricing Endpoint** | Dedicated endpoint for calculating total price with amenities |
| **Amenities API** | Get available amenities per offer |
| **Booking Change Workflow** | Search alternatives and commit changes to existing bookings |
| **Idempotency Support** | Idempotency-Key header for booking and commit-change operations |
| **Multi-Tenancy** | Per-tenant configuration for suppliers, currency, max results |
| **Circuit Breaker** | Resilience4j integration with per-supplier configuration |
| **Rate Limiting** | Global search + per-searchId poll rate limits |
| **Bulkhead Pattern** | Concurrent call limiting |
| **Retry with Backoff** | Automatic retry for transient failures |
| **Async Cancellation Queue** | Non-blocking cancellation with retry logic |
| **Dead Letter Queue** | Failed cancellations tracked for manual resolution |
| **Admin Alert Endpoints** | DLQ monitoring, error metrics, manual alert triggers |
| **Email Alerting Configuration** | Configurable thresholds and recipients |
| **Cancel Status Endpoint** | Check cancellation progress |
| **SkyRide Mock Supplier** | Premium vehicle simulation |
| **Slow Mock Supplier** | Async/polling behavior simulation |
| **Redis Integration** | Distributed caching configuration (Caffeine fallback) |
| **OpenAPI 3.0 Spec** | Complete API documentation in `openapi.yaml` |
| **Virtual Threads** | Java 21 Project Loom for high-throughput I/O |
| **Kubernetes-Ready Health Probes** | Liveness/readiness endpoints |
| **Prometheus Metrics** | Request histograms, error counters, DLQ gauges |

---

## License

This project was developed as part of the Arcube assessment.
