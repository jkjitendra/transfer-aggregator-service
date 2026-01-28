package com.arcube.transferaggregator.controller;

import com.arcube.transferaggregator.dto.BookRequest;
import com.arcube.transferaggregator.dto.BookResponse;
import com.arcube.transferaggregator.dto.CancelResponse;
import com.arcube.transferaggregator.dto.SearchRequest;
import com.arcube.transferaggregator.dto.SearchResponse;
import com.arcube.transferaggregator.dto.SearchSort;
import com.arcube.transferaggregator.exception.GlobalExceptionHandler;
import com.arcube.transferaggregator.exception.RateLimitExceededException;
import com.arcube.transferaggregator.service.SearchPollingService;
import com.arcube.transferaggregator.service.TransferBookingService;
import com.arcube.transferaggregator.service.TransferCancellationService;
import com.arcube.transferaggregator.service.TransferSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransferController.class)
@Import(GlobalExceptionHandler.class)
class TransferControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    TransferSearchService searchService;
    @MockBean
    TransferBookingService bookingService;
    @MockBean
    TransferCancellationService cancellationService;
    @MockBean
    SearchPollingService pollingService;

    @Test
    void searchReturnsOk() throws Exception {
        SearchResponse response = SearchResponse.builder()
            .searchId("s1")
            .offers(List.of())
            .incomplete(false)
            .supplierStatuses(Map.of())
            .build();
        when(searchService.search(any())).thenReturn(response);

        SearchRequest request = SearchRequest.builder()
            .pickupLocation(new SearchRequest.LocationDto("A", null, null, null, null))
            .dropoffLocation(new SearchRequest.LocationDto("B", null, null, null, null))
            .numPassengers(1)
            .build();

        mockMvc.perform(post("/api/v1/transfers/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.searchId").value("s1"));
    }

    @Test
    void searchValidationError() throws Exception {
        SearchRequest request = SearchRequest.builder()
            .dropoffLocation(new SearchRequest.LocationDto("B", null, null, null, null))
            .numPassengers(1)
            .build();

        mockMvc.perform(post("/api/v1/transfers/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void pollUsesFallbackSortForInvalidParams() throws Exception {
        SearchResponse response = SearchResponse.builder()
            .searchId("s1")
            .offers(List.of())
            .incomplete(false)
            .supplierStatuses(Map.of())
            .totalCount(0)
            .page(0)
            .totalPages(0)
            .build();
        when(pollingService.poll(eq("s1"), any(), any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/transfers/search/s1/poll")
                .param("sortBy", "bad")
                .param("sortDir", "bad"))
            .andExpect(status().isOk());

        ArgumentCaptor<SearchSort> sortCaptor = ArgumentCaptor.forClass(SearchSort.class);
        verify(pollingService).poll(eq("s1"), any(), sortCaptor.capture(), any());
        assertThat(sortCaptor.getValue().getField()).isEqualTo(SearchSort.SortField.PRICE);
        assertThat(sortCaptor.getValue().getDirection()).isEqualTo(SearchSort.SortDirection.ASC);
    }

    @Test
    void bookPassesIdempotencyKey() throws Exception {
        BookResponse response = BookResponse.pending("b1");
        when(bookingService.book(any(), eq("idem-1"))).thenReturn(response);

        BookRequest request = BookRequest.builder()
            .offerId("offer")
            .passenger(BookRequest.PassengerDto.builder()
                .firstName("A")
                .lastName("B")
                .email("a@b.com")
                .phoneNumber("1")
                .countryCode("US")
                .build())
            .build();

        mockMvc.perform(post("/api/v1/transfers/book")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "idem-1")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void cancelAndCancelStatus() throws Exception {
        when(cancellationService.cancel("b1")).thenReturn(CancelResponse.pending("b1", "pending"));
        when(cancellationService.getStatus("b1")).thenReturn(CancelResponse.failed("b1", "failed"));

        mockMvc.perform(delete("/api/v1/transfers/bookings/b1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/v1/transfers/bookings/b1/cancel-status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void rateLimitExceptionMappedTo429() throws Exception {
        when(searchService.search(any())).thenThrow(new RateLimitExceededException("too many"));

        SearchRequest request = SearchRequest.builder()
            .pickupLocation(new SearchRequest.LocationDto("A", null, null, null, null))
            .dropoffLocation(new SearchRequest.LocationDto("B", null, null, null, null))
            .numPassengers(1)
            .build();

        mockMvc.perform(post("/api/v1/transfers/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void invalidJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/transfers/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-json}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));
    }
}
