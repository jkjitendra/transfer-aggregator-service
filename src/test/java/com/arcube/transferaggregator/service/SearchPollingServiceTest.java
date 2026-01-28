package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.domain.Money;
import com.arcube.transferaggregator.domain.Offer;
import com.arcube.transferaggregator.domain.Amenity;
import com.arcube.transferaggregator.dto.PageRequest;
import com.arcube.transferaggregator.dto.SearchFilter;
import com.arcube.transferaggregator.dto.SearchResponse;
import com.arcube.transferaggregator.dto.SearchResponse.OfferDto;
import com.arcube.transferaggregator.dto.SearchResponse.SupplierStatusDto;
import com.arcube.transferaggregator.dto.SearchSort;
import com.arcube.transferaggregator.dto.SearchStateDto;
import com.arcube.transferaggregator.ports.SupplierSearchResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SearchPollingServiceTest {

    @Test
    void returnsEmptyWhenCacheMissing() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("search:missing")).thenReturn(null);

        SearchPollingService service = new SearchPollingService(
            Optional.empty(), redis, new ObjectMapper(), new OfferFilterService());

        SearchResponse response = service.poll("missing", null, SearchSort.byPrice(), PageRequest.first());

        assertThat(response.getOffers()).isEmpty();
        assertThat(response.getTotalCount()).isEqualTo(0);
    }

    @Test
    void pollsSlowSupplierAndUpdatesCache() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        SearchStateDto state = SearchStateDto.builder()
            .searchId("s1")
            .offers(List.of(
                OfferDto.builder().offerId("old-slow").supplierCode("SLOW_STUB").build(),
                OfferDto.builder().offerId("old-fast").supplierCode("FAST").build()
            ))
            .statuses(Map.of("SLOW_STUB", SupplierStatusDto.builder().status("POLLING").build()))
            .supplierSearchIds(Map.of("SLOW_STUB", "slow-1"))
            .incomplete(true)
            .build();

        when(ops.get("search:s1")).thenReturn(mapper.writeValueAsString(state));

        Offer offer = Offer.builder()
            .offerId("offer-1")
            .supplierCode("SLOW_STUB")
            .totalPrice(Money.of(50, "USD"))
            .estimatedDurationMinutes(30)
            .expiresAt(Instant.now().plusSeconds(600))
            .build();

        SupplierSearchResult pollResult = SupplierSearchResult.success(
            "SLOW_STUB", "slow-1", List.of(offer), true, 2);

        var slowSupplier = Mockito.mock(com.arcube.transferaggregator.adapters.supplier.mock.SlowMockSupplierClient.class);
        when(slowSupplier.hasSearch("slow-1")).thenReturn(true);
        when(slowSupplier.poll("slow-1")).thenReturn(pollResult);

        OfferFilterService filterService = mock(OfferFilterService.class);
        OfferFilterService.FilterResult filterResult = OfferFilterService.FilterResult.builder()
            .offers(List.of(OfferDto.builder().offerId("offer-1").supplierCode("SLOW_STUB").build()))
            .totalCount(1)
            .page(0)
            .size(1)
            .totalPages(1)
            .build();
        when(filterService.filterAndSort(any(), any(), any(), any())).thenReturn(filterResult);

        SearchPollingService service = new SearchPollingService(
            Optional.of(slowSupplier), redis, mapper, filterService);

        SearchResponse response = service.poll("s1", SearchFilter.builder().build(), SearchSort.byPrice(), PageRequest.first());

        assertThat(response.getOffers()).hasSize(1);
        assertThat(response.isIncomplete()).isFalse();
        assertThat(response.getSupplierStatuses().get("SLOW_STUB").getStatus()).isEqualTo("SUCCESS");

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(ops).set(eq("search:s1"), jsonCaptor.capture(), any());
        assertThat(jsonCaptor.getValue()).contains("\"searchId\":\"s1\"");
    }

    @Test
    void cachesSearchStateAndHandlesSerializationFailure() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        SearchPollingService service = new SearchPollingService(
            Optional.empty(), redis, mapper, new OfferFilterService());

        SearchResponse response = SearchResponse.builder()
            .searchId("s-cache")
            .offers(List.of())
            .supplierStatuses(Map.of())
            .incomplete(true)
            .build();

        service.cacheSearchState("s-cache", response, Map.of("S1", "sid-1"));
        verify(ops).set(eq("search:s-cache"), anyString(), any());

        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any()))
            .thenThrow(new JsonProcessingException("boom") {});
        SearchPollingService failingService = new SearchPollingService(
            Optional.empty(), redis, failingMapper, new OfferFilterService());

        failingService.cacheSearchState("s-cache", response, Map.of());
        verify(ops, times(1)).set(eq("search:s-cache"), anyString(), any());
    }

    @Test
    void returnsEmptyOnInvalidJson() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("search:bad")).thenReturn("{not-json}");

        SearchPollingService service = new SearchPollingService(
            Optional.empty(), redis, new ObjectMapper(), new OfferFilterService());

        SearchResponse response = service.poll("bad", null, SearchSort.byPrice(), PageRequest.first());

        assertThat(response.getOffers()).isEmpty();
        assertThat(response.isIncomplete()).isFalse();
    }

    @Test
    void doesNotPollSlowSupplierWhenIncompleteFalse() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        SearchStateDto state = SearchStateDto.builder()
            .searchId("s2")
            .offers(List.of(OfferDto.builder().offerId("o1").supplierCode("SLOW_STUB").build()))
            .statuses(Map.of())
            .supplierSearchIds(Map.of("SLOW_STUB", "slow-1"))
            .incomplete(false)
            .build();
        when(ops.get("search:s2")).thenReturn(mapper.writeValueAsString(state));

        var slowSupplier = mock(com.arcube.transferaggregator.adapters.supplier.mock.SlowMockSupplierClient.class);
        SearchPollingService service = new SearchPollingService(
            Optional.of(slowSupplier), redis, mapper, new OfferFilterService());

        SearchResponse response = service.poll("s2", null, SearchSort.byPrice(), PageRequest.first());
        assertThat(response.isIncomplete()).isFalse();
        verifyNoInteractions(slowSupplier);
    }

    @Test
    void skipsSlowSupplierWhenSearchIdMissing() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        SearchStateDto state = SearchStateDto.builder()
            .searchId("s3")
            .offers(List.of())
            .statuses(Map.of())
            .supplierSearchIds(Map.of())
            .incomplete(true)
            .build();
        when(ops.get("search:s3")).thenReturn(mapper.writeValueAsString(state));

        var slowSupplier = mock(com.arcube.transferaggregator.adapters.supplier.mock.SlowMockSupplierClient.class);
        SearchPollingService service = new SearchPollingService(
            Optional.of(slowSupplier), redis, mapper, new OfferFilterService());

        SearchResponse response = service.poll("s3", null, SearchSort.byPrice(), PageRequest.first());
        assertThat(response.isIncomplete()).isFalse();
        verifyNoInteractions(slowSupplier);
    }

    @Test
    void pollsSlowSupplierWhenIncompleteAndMarksPolling() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        SearchStateDto state = SearchStateDto.builder()
            .searchId("s4")
            .offers(List.of())
            .statuses(Map.of("SLOW_STUB", SupplierStatusDto.builder().status("POLLING").build()))
            .supplierSearchIds(Map.of("SLOW_STUB", "slow-2"))
            .incomplete(true)
            .build();
        when(ops.get("search:s4")).thenReturn(mapper.writeValueAsString(state));

        Offer offer = Offer.builder()
            .offerId("offer-2")
            .supplierCode("SLOW_STUB")
            .includedAmenities(List.of(Amenity.included("wifi", "WiFi")))
            .estimatedDurationMinutes(10)
            .expiresAt(Instant.now().plusSeconds(600))
            .build();

        SupplierSearchResult pollResult = SupplierSearchResult.success(
            "SLOW_STUB", "slow-2", List.of(offer), false, 2);

        var slowSupplier = mock(com.arcube.transferaggregator.adapters.supplier.mock.SlowMockSupplierClient.class);
        when(slowSupplier.hasSearch("slow-2")).thenReturn(true);
        when(slowSupplier.poll("slow-2")).thenReturn(pollResult);

        SearchPollingService service = new SearchPollingService(
            Optional.of(slowSupplier), redis, mapper, new OfferFilterService());

        SearchResponse response = service.poll("s4", null, SearchSort.byPrice(), PageRequest.first());
        assertThat(response.isIncomplete()).isTrue();
        assertThat(response.getSupplierStatuses().get("SLOW_STUB").getStatus()).isEqualTo("POLLING");
        assertThat(response.getOffers()).hasSize(1);
        assertThat(response.getOffers().get(0).getIncludedAmenities()).contains("wifi");
    }

    @Test
    void doesNotPollSlowSupplierWhenHasSearchFalse() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        SearchStateDto state = SearchStateDto.builder()
            .searchId("s6")
            .offers(List.of())
            .statuses(Map.of())
            .supplierSearchIds(Map.of("SLOW_STUB", "slow-missing"))
            .incomplete(true)
            .build();
        when(ops.get("search:s6")).thenReturn(mapper.writeValueAsString(state));

        var slowSupplier = mock(com.arcube.transferaggregator.adapters.supplier.mock.SlowMockSupplierClient.class);
        when(slowSupplier.hasSearch("slow-missing")).thenReturn(false);

        SearchPollingService service = new SearchPollingService(
            Optional.of(slowSupplier), redis, mapper, new OfferFilterService());

        SearchResponse response = service.poll("s6", null, SearchSort.byPrice(), PageRequest.first());
        assertThat(response.getOffers()).isEmpty();
        verify(slowSupplier, never()).poll(anyString());
    }

    @Test
    void pollsSlowSupplierWithNullAmenities() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        SearchStateDto state = SearchStateDto.builder()
            .searchId("s7")
            .offers(List.of())
            .statuses(Map.of("SLOW_STUB", SupplierStatusDto.builder().status("POLLING").build()))
            .supplierSearchIds(Map.of("SLOW_STUB", "slow-3"))
            .incomplete(true)
            .build();
        when(ops.get("search:s7")).thenReturn(mapper.writeValueAsString(state));

        Offer offer = Offer.builder()
            .offerId("offer-3")
            .supplierCode("SLOW_STUB")
            .includedAmenities(null)
            .estimatedDurationMinutes(10)
            .expiresAt(Instant.now().plusSeconds(600))
            .build();

        SupplierSearchResult pollResult = SupplierSearchResult.success(
            "SLOW_STUB", "slow-3", List.of(offer), true, 1);

        var slowSupplier = mock(com.arcube.transferaggregator.adapters.supplier.mock.SlowMockSupplierClient.class);
        when(slowSupplier.hasSearch("slow-3")).thenReturn(true);
        when(slowSupplier.poll("slow-3")).thenReturn(pollResult);

        SearchPollingService service = new SearchPollingService(
            Optional.of(slowSupplier), redis, mapper, new OfferFilterService());

        SearchResponse response = service.poll("s7", null, SearchSort.byPrice(), PageRequest.first());
        assertThat(response.getOffers()).hasSize(1);
        assertThat(response.getOffers().get(0).getIncludedAmenities()).isEmpty();
        assertThat(response.getSupplierStatuses().get("SLOW_STUB").getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void handlesUpdateCacheSerializationFailure() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        SearchStateDto state = SearchStateDto.builder()
            .searchId("s5")
            .offers(List.of())
            .statuses(Map.of())
            .supplierSearchIds(Map.of())
            .incomplete(true)
            .build();
        when(ops.get("search:s5")).thenReturn(mapper.writeValueAsString(state));

        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.readValue(any(String.class), eq(SearchStateDto.class))).thenReturn(state);
        when(failingMapper.writeValueAsString(any()))
            .thenThrow(new JsonProcessingException("bad") {});

        SearchPollingService service = new SearchPollingService(
            Optional.empty(), redis, failingMapper, new OfferFilterService());

        SearchResponse response = service.poll("s5", null, SearchSort.byPrice(), PageRequest.first());
        assertThat(response.getSearchId()).isEqualTo("s5");
    }

    @Test
    void pollNoArgsDelegatesDefaults() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("search:missing")).thenReturn(null);

        SearchPollingService service = new SearchPollingService(
            Optional.empty(), redis, new ObjectMapper(), new OfferFilterService());

        SearchResponse response = service.poll("missing");
        assertThat(response.getSearchId()).isEqualTo("missing");
        assertThat(response.getOffers()).isEmpty();
    }
}
