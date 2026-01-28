package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.config.AggregatorProperties;
import com.arcube.transferaggregator.config.TenantConfig;
import com.arcube.transferaggregator.config.TenantContext;
import com.arcube.transferaggregator.domain.Amenity;
import com.arcube.transferaggregator.domain.Offer;
import com.arcube.transferaggregator.domain.SearchCommand;
import com.arcube.transferaggregator.dto.SearchRequest;
import com.arcube.transferaggregator.dto.SearchResponse;
import com.arcube.transferaggregator.ports.SupplierRegistry;
import com.arcube.transferaggregator.ports.SupplierSearchResult;
import com.arcube.transferaggregator.ports.TransferSupplier;
import com.arcube.transferaggregator.resilience.RateLimiter;
import com.arcube.transferaggregator.resilience.SupplierBulkhead;
import com.arcube.transferaggregator.resilience.SupplierCircuitBreaker;
import com.arcube.transferaggregator.testutil.ArcubeTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TransferSearchServiceTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void aggregatesSuppliersAndCachesIncomplete() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        SupplierBulkhead bulkhead = mock(SupplierBulkhead.class);
        RateLimiter rateLimiter = mock(RateLimiter.class);
        SearchPollingService pollingService = mock(SearchPollingService.class);
        SupplierCircuitBreaker circuitBreaker = mock(SupplierCircuitBreaker.class);
        TenantConfig tenantConfig = mock(TenantConfig.class);

        when(props.getGlobalTimeoutSeconds()).thenReturn(5);
        when(tenantConfig.getDefaultTenant()).thenReturn("default");
        when(tenantConfig.isSupplierEnabled(anyString(), anyString())).thenReturn(true);

        TransferSupplier s1 = mock(TransferSupplier.class);
        when(s1.getSupplierCode()).thenReturn("S1");
        TransferSupplier s2 = mock(TransferSupplier.class);
        when(s2.getSupplierCode()).thenReturn("S2");

        when(registry.getEnabledSuppliers()).thenReturn(List.of(s1, s2));
        when(circuitBreaker.isOpen(anyString())).thenReturn(false);
        when(bulkhead.execute(Mockito.<java.util.function.Supplier<Object>>any()))
            .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(circuitBreaker.execute(anyString(), any(), any())).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(1)).get());

        Offer offer1 = Offer.builder()
            .offerId("o1")
            .supplierCode("S1")
            .includedAmenities(List.of(Amenity.included("wifi", "WiFi")))
            .expiresAt(Instant.now().plusSeconds(600))
            .build();
        Offer offer2 = Offer.builder()
            .offerId("o2")
            .supplierCode("S2")
            .expiresAt(Instant.now().plusSeconds(600))
            .build();

        when(s1.search(any(), any(Duration.class))).thenReturn(
            SupplierSearchResult.success("S1", "sid-1", List.of(offer1), true, 1));
        when(s2.search(any(), any(Duration.class))).thenReturn(
            SupplierSearchResult.success("S2", "sid-2", List.of(offer2), false, 1));

        TransferSearchService service = new TransferSearchService(
            registry, props, bulkhead, rateLimiter, pollingService, circuitBreaker, tenantConfig);

        TenantContext.setTenantId("tenant-a");
        SearchRequest request = ArcubeTestData.validAddressSearch();

        SearchResponse response = service.search(request);

        assertThat(response.getOffers()).hasSize(2);
        assertThat(response.isIncomplete()).isTrue();
        assertThat(response.getSupplierStatuses()).containsKeys("S1", "S2");

        ArgumentCaptor<SearchResponse> responseCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        verify(pollingService).cacheSearchState(anyString(), responseCaptor.capture(), anyMap());
        assertThat(responseCaptor.getValue().isIncomplete()).isTrue();
    }

    @Test
    void marksCircuitOpenSuppliers() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        SupplierBulkhead bulkhead = mock(SupplierBulkhead.class);
        RateLimiter rateLimiter = mock(RateLimiter.class);
        SearchPollingService pollingService = mock(SearchPollingService.class);
        SupplierCircuitBreaker circuitBreaker = mock(SupplierCircuitBreaker.class);
        TenantConfig tenantConfig = mock(TenantConfig.class);

        when(props.getGlobalTimeoutSeconds()).thenReturn(5);
        when(tenantConfig.getDefaultTenant()).thenReturn("default");
        when(tenantConfig.isSupplierEnabled(anyString(), anyString())).thenReturn(true);

        TransferSupplier s1 = mock(TransferSupplier.class);
        when(s1.getSupplierCode()).thenReturn("S1");
        TransferSupplier s2 = mock(TransferSupplier.class);
        when(s2.getSupplierCode()).thenReturn("S2");

        when(registry.getEnabledSuppliers()).thenReturn(List.of(s1, s2));
        when(circuitBreaker.isOpen("S1")).thenReturn(true);
        when(circuitBreaker.isOpen("S2")).thenReturn(false);
        when(bulkhead.execute(Mockito.<java.util.function.Supplier<Object>>any()))
            .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(circuitBreaker.execute(anyString(), any(), any())).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(1)).get());

        when(s2.search(any(), any(Duration.class))).thenReturn(
            SupplierSearchResult.success("S2", "sid-2", List.of(), true, 1));

        TransferSearchService service = new TransferSearchService(
            registry, props, bulkhead, rateLimiter, pollingService, circuitBreaker, tenantConfig);

        SearchResponse response = service.search(ArcubeTestData.validAddressSearch());

        assertThat(response.isIncomplete()).isTrue();
        assertThat(response.getSupplierStatuses().get("S1").getStatus()).isEqualTo("CIRCUIT_OPEN");
    }

    @Test
    void returnsEmptyWhenNoSuppliersEnabled() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        SupplierBulkhead bulkhead = mock(SupplierBulkhead.class);
        RateLimiter rateLimiter = mock(RateLimiter.class);
        SearchPollingService pollingService = mock(SearchPollingService.class);
        SupplierCircuitBreaker circuitBreaker = mock(SupplierCircuitBreaker.class);
        TenantConfig tenantConfig = mock(TenantConfig.class);

        when(props.getGlobalTimeoutSeconds()).thenReturn(5);
        when(tenantConfig.getDefaultTenant()).thenReturn("default");
        when(registry.getEnabledSuppliers()).thenReturn(List.of());

        TransferSearchService service = new TransferSearchService(
            registry, props, bulkhead, rateLimiter, pollingService, circuitBreaker, tenantConfig);

        SearchResponse response = service.search(ArcubeTestData.validAddressSearch());

        assertThat(response.getOffers()).isEmpty();
        assertThat(response.isIncomplete()).isTrue();
        verifyNoInteractions(pollingService);
    }

    @Test
    void marksTimeoutAndIncompleteWhenSupplierTimesOut() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        SupplierBulkhead bulkhead = mock(SupplierBulkhead.class);
        RateLimiter rateLimiter = mock(RateLimiter.class);
        SearchPollingService pollingService = mock(SearchPollingService.class);
        SupplierCircuitBreaker circuitBreaker = mock(SupplierCircuitBreaker.class);
        TenantConfig tenantConfig = mock(TenantConfig.class);

        when(props.getGlobalTimeoutSeconds()).thenReturn(5);
        when(tenantConfig.getDefaultTenant()).thenReturn("default");
        when(tenantConfig.isSupplierEnabled(anyString(), anyString())).thenReturn(true);

        TransferSupplier supplier = mock(TransferSupplier.class);
        when(supplier.getSupplierCode()).thenReturn("S1");
        when(registry.getEnabledSuppliers()).thenReturn(List.of(supplier));
        when(circuitBreaker.isOpen(anyString())).thenReturn(false);
        when(bulkhead.execute(Mockito.<java.util.function.Supplier<Object>>any()))
            .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(circuitBreaker.execute(anyString(), any(), any()))
            .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(1)).get());

        SupplierSearchResult timedOut = new SupplierSearchResult(
            "S1", "sid-1", List.of(), true, true, 1, null);
        when(supplier.search(any(), any(Duration.class))).thenReturn(timedOut);

        TransferSearchService service = new TransferSearchService(
            registry, props, bulkhead, rateLimiter, pollingService, circuitBreaker, tenantConfig);

        SearchResponse response = service.search(ArcubeTestData.validAddressSearch());

        assertThat(response.isIncomplete()).isTrue();
        assertThat(response.getSupplierStatuses().get("S1").getStatus()).isEqualTo("TIMEOUT");
        verify(pollingService).cacheSearchState(anyString(), any(SearchResponse.class), anyMap());
    }

    @Test
    void handlesSupplierExceptionAndMarksError() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        SupplierBulkhead bulkhead = mock(SupplierBulkhead.class);
        RateLimiter rateLimiter = mock(RateLimiter.class);
        SearchPollingService pollingService = mock(SearchPollingService.class);
        SupplierCircuitBreaker circuitBreaker = mock(SupplierCircuitBreaker.class);
        TenantConfig tenantConfig = mock(TenantConfig.class);

        when(props.getGlobalTimeoutSeconds()).thenReturn(5);
        when(tenantConfig.getDefaultTenant()).thenReturn("default");
        when(tenantConfig.isSupplierEnabled(anyString(), anyString())).thenReturn(true);

        TransferSupplier supplier = mock(TransferSupplier.class);
        when(supplier.getSupplierCode()).thenReturn("S1");
        when(registry.getEnabledSuppliers()).thenReturn(List.of(supplier));
        when(circuitBreaker.isOpen(anyString())).thenReturn(false);
        when(bulkhead.execute(Mockito.<java.util.function.Supplier<Object>>any()))
            .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(circuitBreaker.execute(anyString(), any(), any()))
            .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(1)).get());

        when(supplier.search(any(), any(Duration.class))).thenThrow(new RuntimeException("boom"));

        TransferSearchService service = new TransferSearchService(
            registry, props, bulkhead, rateLimiter, pollingService, circuitBreaker, tenantConfig);

        SearchResponse response = service.search(ArcubeTestData.validAddressSearch());

        assertThat(response.isIncomplete()).isTrue();
        assertThat(response.getSupplierStatuses().get("S1").getStatus()).isEqualTo("ERROR");
        verify(pollingService).cacheSearchState(anyString(), any(SearchResponse.class), anyMap());
    }

    @Test
    void usesCircuitBreakerFallbackResult() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        SupplierBulkhead bulkhead = mock(SupplierBulkhead.class);
        RateLimiter rateLimiter = mock(RateLimiter.class);
        SearchPollingService pollingService = mock(SearchPollingService.class);
        SupplierCircuitBreaker circuitBreaker = mock(SupplierCircuitBreaker.class);
        TenantConfig tenantConfig = mock(TenantConfig.class);

        when(props.getGlobalTimeoutSeconds()).thenReturn(5);
        when(tenantConfig.getDefaultTenant()).thenReturn("default");
        when(tenantConfig.isSupplierEnabled(anyString(), anyString())).thenReturn(true);

        TransferSupplier supplier = mock(TransferSupplier.class);
        when(supplier.getSupplierCode()).thenReturn("S1");
        when(registry.getEnabledSuppliers()).thenReturn(List.of(supplier));
        when(circuitBreaker.isOpen(anyString())).thenReturn(false);
        when(bulkhead.execute(Mockito.<java.util.function.Supplier<Object>>any()))
            .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(circuitBreaker.execute(anyString(), any(), any()))
            .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(2)).get());

        TransferSearchService service = new TransferSearchService(
            registry, props, bulkhead, rateLimiter, pollingService, circuitBreaker, tenantConfig);

        SearchResponse response = service.search(ArcubeTestData.validAddressSearch());

        assertThat(response.getSupplierStatuses().get("S1").getStatus()).isEqualTo("SUCCESS");
        verifyNoInteractions(pollingService);
    }

    @Test
    void mapsTransferModeWhenProvidedAndHandlesNullLocations() throws Exception {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        SupplierBulkhead bulkhead = mock(SupplierBulkhead.class);
        RateLimiter rateLimiter = mock(RateLimiter.class);
        SearchPollingService pollingService = mock(SearchPollingService.class);
        SupplierCircuitBreaker circuitBreaker = mock(SupplierCircuitBreaker.class);
        TenantConfig tenantConfig = mock(TenantConfig.class);

        when(props.getGlobalTimeoutSeconds()).thenReturn(5);
        when(tenantConfig.getDefaultTenant()).thenReturn("default");
        when(tenantConfig.isSupplierEnabled(anyString(), anyString())).thenReturn(true);

        TransferSupplier supplier = mock(TransferSupplier.class);
        when(supplier.getSupplierCode()).thenReturn("S1");
        when(registry.getEnabledSuppliers()).thenReturn(List.of(supplier));
        when(circuitBreaker.isOpen(anyString())).thenReturn(false);
        when(bulkhead.execute(Mockito.<java.util.function.Supplier<Object>>any()))
            .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(circuitBreaker.execute(anyString(), any(), any()))
            .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(1)).get());

        when(supplier.search(any(), any(Duration.class))).thenReturn(
            SupplierSearchResult.success("S1", "sid-1", List.of(), true, 1));

        TransferSearchService service = new TransferSearchService(
            registry, props, bulkhead, rateLimiter, pollingService, circuitBreaker, tenantConfig);

        SearchRequest request = SearchRequest.builder()
            .pickupLocation(null)
            .dropoffLocation(null)
            .mode(SearchRequest.TransferModeDto.ROUND_TRIP)
            .build();

        service.search(request);

        ArgumentCaptor<SearchCommand> captor = ArgumentCaptor.forClass(SearchCommand.class);
        verify(supplier).search(captor.capture(), any(Duration.class));
        assertThat(captor.getValue().mode()).isEqualTo(SearchCommand.TransferMode.ROUND_TRIP);
        assertThat(captor.getValue().pickupLocation()).isNull();
        assertThat(captor.getValue().dropoffLocation()).isNull();
    }

    @Test
    void usesDefaultModeAndMapsNullAmenities() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        SupplierBulkhead bulkhead = mock(SupplierBulkhead.class);
        RateLimiter rateLimiter = mock(RateLimiter.class);
        SearchPollingService pollingService = mock(SearchPollingService.class);
        SupplierCircuitBreaker circuitBreaker = mock(SupplierCircuitBreaker.class);
        TenantConfig tenantConfig = mock(TenantConfig.class);

        when(props.getGlobalTimeoutSeconds()).thenReturn(5);
        when(tenantConfig.getDefaultTenant()).thenReturn("default");
        when(tenantConfig.isSupplierEnabled(anyString(), anyString())).thenReturn(true);

        TransferSupplier supplier = mock(TransferSupplier.class);
        when(supplier.getSupplierCode()).thenReturn("S1");
        when(registry.getEnabledSuppliers()).thenReturn(List.of(supplier));
        when(circuitBreaker.isOpen(anyString())).thenReturn(false);
        when(bulkhead.execute(Mockito.<java.util.function.Supplier<Object>>any()))
            .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(circuitBreaker.execute(anyString(), any(), any()))
            .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(1)).get());

        Offer offer = Offer.builder()
            .offerId("o1")
            .supplierCode("S1")
            .includedAmenities(null)
            .build();

        when(supplier.search(any(), any(Duration.class))).thenReturn(
            SupplierSearchResult.success("S1", "sid-1", List.of(offer), true, 1));

        TransferSearchService service = new TransferSearchService(
            registry, props, bulkhead, rateLimiter, pollingService, circuitBreaker, tenantConfig);

        SearchRequest request = SearchRequest.builder()
            .mode(null)
            .build();

        SearchResponse response = service.search(request);

        assertThat(response.getOffers()).hasSize(1);
        assertThat(response.getOffers().get(0).getIncludedAmenities()).isEmpty();

        ArgumentCaptor<SearchCommand> captor = ArgumentCaptor.forClass(SearchCommand.class);
        verify(supplier).search(captor.capture(), any(Duration.class));
        assertThat(captor.getValue().mode()).isEqualTo(SearchCommand.TransferMode.ONE_WAY);
    }

    @Test
    void usesMinimumTimeoutWhenDeadlineAlreadyPassed() {
        SupplierRegistry registry = mock(SupplierRegistry.class);
        AggregatorProperties props = mock(AggregatorProperties.class);
        SupplierBulkhead bulkhead = mock(SupplierBulkhead.class);
        RateLimiter rateLimiter = mock(RateLimiter.class);
        SearchPollingService pollingService = mock(SearchPollingService.class);
        SupplierCircuitBreaker circuitBreaker = mock(SupplierCircuitBreaker.class);
        TenantConfig tenantConfig = mock(TenantConfig.class);

        when(props.getGlobalTimeoutSeconds()).thenReturn(-1);
        when(tenantConfig.getDefaultTenant()).thenReturn("default");
        when(tenantConfig.isSupplierEnabled(anyString(), anyString())).thenReturn(true);

        TransferSupplier supplier = mock(TransferSupplier.class);
        when(supplier.getSupplierCode()).thenReturn("S1");
        when(registry.getEnabledSuppliers()).thenReturn(List.of(supplier));
        when(circuitBreaker.isOpen(anyString())).thenReturn(false);
        when(bulkhead.execute(Mockito.<java.util.function.Supplier<Object>>any()))
            .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(circuitBreaker.execute(anyString(), any(), any()))
            .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(1)).get());

        when(supplier.search(any(), any(Duration.class))).thenReturn(
            SupplierSearchResult.success("S1", "sid-1", List.of(), true, 1));

        TransferSearchService service = new TransferSearchService(
            registry, props, bulkhead, rateLimiter, pollingService, circuitBreaker, tenantConfig);

        service.search(ArcubeTestData.validAddressSearch());

        ArgumentCaptor<Duration> timeoutCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(supplier).search(any(), timeoutCaptor.capture());
        assertThat(timeoutCaptor.getValue().toMillis()).isEqualTo(100);
    }
}
