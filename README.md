# Airport Transfer Aggregator Microservice

A high-performance, scalable microservice for aggregating airport transfer providers (like Mozio) into a unified API. Built for Arcube's ancillary platform.

---

## Architecture

This service is built using **Hexagonal Architecture (Ports and Adapters)** to ensure domain logic remains isolated from external concerns.

### Key Components:
- **Domain Layer**: Core business logic (`Offer`, `Booking`, `SearchCommand`). Pure Java, no framework dependencies.
- **Ports (Input)**: REST Controllers exposing functionality to the outside world.
- **Ports (Output)**: Interfaces (`TransferSupplier`, `SupplierRegistry`) defining contracts for external suppliers.
- **Adapters**: Implementations for specific technologies (Mozio HTTP Client, InMemory Repository, Kafka Producers).

### Extensibility
Adding a new supplier (e.g., "SkyRide") is straightforward:
1. Implement the `TransferSupplier` interface.
2. Create the necessary HTTP client/adapter.
3. Register the bean - the `SupplierRegistry` automatically detects and aggregates it.

---

## 🛠️ Technology Stack

- **Language**: Java 21 (LTS)
- **Framework**: Spring Boot 3.5.9
- **Concurrency**: Virtual Threads (Project Loom) for high-throughput I/O
- **Reactive Client**: Spring WebFlux (WebClient) for non-blocking upstream calls
- **Resilience**: Rate Limiting, Bulkheads, and Retries (custom implementation)
- **Caching**: Caffeine for search results
- **Observability**: Micrometer, Prometheus, and Zipkin-compatible JSON logging

---

## Getting Started

### Clone & Setup
```bash
# Clone the repository
git clone https://github.com/jkjitendra/transfer-aggregator-service.git

# Navigate into the project directory
cd transfer-aggregator-service
```

### Prerequisites
- **Java 21+** installed (`java -version`)
- **Maven** (wrapper included)

### Build
To build the application and run tests:
```bash
./mvnw clean install
```

### Run Locally
Start the application on port 8080:
```bash
./mvnw spring-boot:run
```

The service will start with **Mock Suppliers** enabled by default, so you can test without external API keys.

---

## Configuration

The application is configured via `application.yml`. Key environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `AGGREGATOR_MODE` | `stub` for testing, `real` for production | `stub` |
| `MOZIO_ENABLED` | Enable Mozio integration | `true` |
| `MOZIO_API_KEY` | API Key for Mozio (required if enabled) | `(empty)` |
| `TOKEN_SECRET` | Secret for HMAC signing of IDs | `[random-default]` |
| `LOG_LEVEL` | Logging level | `INFO` |

---

## API Documentation

### Interactive Docs (Swagger UI)
Once running, verify the API is up:
**[http://localhost:8080](http://localhost:8080)** (Redirects to Swagger UI)

### Core Endpoints

#### 1. Search Transfers
`POST /api/v1/transfers/search`
- **Input**: Origin/Dest lat/long, date, passengers.
- **Output**: List of offers and a `searchId`.
- **Behavior**: Aggregates results from all enabled suppliers in parallel.

#### 2. Poll Results (Async)
`GET /api/v1/transfers/search/{searchId}/poll`
- **Input**: `searchId` from previous step.
- **Output**: Updated offers and `complete` status.
- **Use Case**: For suppliers (like Mozio) that return results asynchronously.

#### 3. Book Transfer
`POST /api/v1/transfers/book`
- **Input**: `offerId` (contains encrypted supplier context), passenger details.
- **Output**: Booking confirmation and `bookingId`.

#### 4. Cancel Booking
`DELETE /api/v1/transfers/bookings/{bookingId}`
- **Input**: `bookingId`.
- **Output**: Cancellation status and refund amount.

---

## Testing

### Integration Tests
Run the standard test suite:
```bash
./mvnw test
```

### Manual Testing with Postman
A complete Postman collection is included in the repo:
`postman/AggregatorService.postman_collection.json`

**How to use:**
1. Import the collection into Postman.
2. Import the environment file `postman/AggregatorService.postman_environment.json`.
3. Start the application (`./mvnw spring-boot:run`).
4. Run requests in order: **Search -> Poll -> Book -> Cancel**.

### Mock Scenarios
When running in `stub` mode (default), you can test resilience patterns:
- **Slow Mock**: Updates periodically to test polling logic.
- **SkyRide Mock**: Simulates robust/premium inventory.

---

## Design Decisions

### 1. Handling Async Suppliers
Since endpoints like Mozio are asynchronous, this service implements a **Polling Pattern**.
- The initial Search returns what's available immediately.
- The Client polls the `/poll` endpoint with the `searchId`.
- The service caches results and merges new ones until the supplier indicates completion.

### 2. Stateless ID Tokens
To avoid complex state management for every search:
- `offerId` and `bookingId` are **Base64-encoded, HMAC-signed tokens**.
- They contain necessary context (Supplier ID, original price, timestamps).
- This prevents ID enumeration attacks and allows the server to validate requests without a database lookup for search validation.

### 3. Virtual Threads
We use Java 21 Virtual Threads (`spring.threads.virtual.enabled=true`) to handle high concurrency. This allows blocking style code (easier to read/maintain) to scale like reactive code.


