package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.config.AggregatorProperties;
import com.arcube.transferaggregator.config.TenantConfig;
import com.arcube.transferaggregator.domain.SearchCommand;
import com.arcube.transferaggregator.ports.SupplierRegistry;
import com.arcube.transferaggregator.ports.SupplierSearchResult;
import com.arcube.transferaggregator.ports.TransferSupplier;
import com.arcube.transferaggregator.resilience.RateLimiter;
import com.arcube.transferaggregator.resilience.SupplierBulkhead;
import com.arcube.transferaggregator.resilience.SupplierCircuitBreaker;
import com.arcube.transferaggregator.testutil.ArcubeTestData;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Opt-in local load gate; excludes public routing and external suppliers. */
@EnabledIfEnvironmentVariable(named = "TRANSFER_RUN_LOAD_TEST", matches = "true")
class SearchConcurrencyIT {
    @Test
    void twentyConcurrentSearchesStayWithinLatencyAndBulkheadBudget() throws Exception {
        AggregatorProperties properties = new AggregatorProperties();
        properties.setGlobalTimeoutSeconds(3);
        properties.getResilience().setMaxConcurrentCalls(5);
        properties.getResilience().setSearchRateLimitPerMinute(100);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        TransferSupplier supplier = new TransferSupplier() {
            public String getSupplierCode() { return "STUB"; }
            public String getSupplierName() { return "Load fixture"; }
            public boolean isEnabled() { return true; }
            public SupplierSearchResult search(SearchCommand command, Duration timeout) {
                int current = active.incrementAndGet();
                peak.accumulateAndGet(current, Math::max);
                try { Thread.sleep(100); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { active.decrementAndGet(); }
                return SupplierSearchResult.success("STUB", "search", List.of(), true, 1);
            }
            public com.arcube.transferaggregator.ports.SupplierBookingResult book(
                    com.arcube.transferaggregator.domain.BookCommand command, Duration timeout) {
                throw new UnsupportedOperationException();
            }
            public com.arcube.transferaggregator.ports.SupplierCancelResult cancel(
                    com.arcube.transferaggregator.domain.CancelCommand command) {
                throw new UnsupportedOperationException();
            }
        };
        SupplierRegistry registry = mock(SupplierRegistry.class);
        when(registry.getEnabledSuppliers()).thenReturn(List.of(supplier));
        TenantConfig tenants = mock(TenantConfig.class);
        when(tenants.getDefaultTenant()).thenReturn("default");
        when(tenants.getTenant("default")).thenReturn(new AggregatorProperties.TenantProperties());
        when(tenants.isSupplierEnabled(anyString(), anyString())).thenReturn(true);
        TransferSearchService service = new TransferSearchService(registry, properties,
            new SupplierBulkhead(properties), new RateLimiter(properties), mock(SearchPollingService.class),
            new SupplierCircuitBreaker(CircuitBreakerRegistry.ofDefaults()), tenants);
        List<Long> durations = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Long>> results = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                results.add(executor.submit(() -> {
                    long start = System.nanoTime();
                    var response = service.search(ArcubeTestData.validAddressSearch());
                    assertThat(response.getSupplierStatuses().get("STUB").getStatus()).isEqualTo("SUCCESS");
                    return Duration.ofNanos(System.nanoTime() - start).toMillis();
                }));
            }
            for (Future<Long> result : results) durations.add(result.get());
        }
        durations.sort(Long::compareTo);
        assertThat(durations.get(18)).isLessThan(2000L); // p95 of 20 requests
        assertThat(peak.get()).isLessThanOrEqualTo(5);
    }
}
