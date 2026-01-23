# Project Status & Changes

## Current Issues & Challenges
- **Scalability**: The current implementation handles caching in-memory (Caffeine), which limits scalability across multiple instances.
- **Resilience**: There is limited protection against supplier failures (no circuit breaker), which can lead to cascading failures or slow responses.
- **Flexibility**: Configuration is hardcoded, lacking multi-tenant support for different supplier preferences.
- **User Experience**: Missing key features like filtering, sorting, and booking modifications.

## Summary of Changes
We are upgrading the Transfer Aggregator Service to be production-ready, resilient, and feature-rich.

### 1. Distributed Caching (Redis)
- **Problem**: In-memory cache is stateful and prevents horizontal scaling.
- **Solution**: Replacing Caffeine with **Redis**. This allows multiple instances of the service to share search results and polling states seamlessly.

### 2. Resilience (Circuit Breaker)
- **Problem**: Slow or down suppliers can block threads and degrade overall system performance.
- **Solution**: Integrating **Resilience4j Circuit Breaker**. If a supplier fails repeatedly, we "fail fast" for that supplier, allowing the system to remain responsive and recover automatically.

### 3. Edit Booking Capability
- **Problem**: functionality to modify an existing reservation was missing.
- **Solution**: Added new routes and logic to support the "Edit Booking" flow:
    - `POST /bookings/{id}/search-changes`: Check availability for new criteria.
    - `POST /bookings/{id}/commit-change`: Confirm the modification.

### 4. Search Filtering & Alerting
- **New Features**:
    - **Filtering**: Added filters for price, amenities, and vehicle attributes to refine search results.
    - **Amenities Pricing**: Logic to calculate and display amenity costs clearly.
    - **Alerting**: Automated email alerts and metrics (via Prometheus/Grafana) for high error rates or DLQ backups.

### 5. Multi-Tenancy
- **Feature**: Added configuration to support different preferences (e.g., preferred suppliers) per airline/tenant.
