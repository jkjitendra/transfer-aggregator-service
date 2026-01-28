package com.arcube.transferaggregator.controller;

import com.arcube.transferaggregator.dto.BookingChangeCommitRequest;
import com.arcube.transferaggregator.dto.BookingChangeResponse;
import com.arcube.transferaggregator.dto.BookingChangeSearchRequest;
import com.arcube.transferaggregator.dto.SearchResponse;
import com.arcube.transferaggregator.exception.GlobalExceptionHandler;
import com.arcube.transferaggregator.service.TransferBookingChangeService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingChangeController.class)
@Import(GlobalExceptionHandler.class)
class BookingChangeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    TransferBookingChangeService bookingChangeService;

    @Test
    void searchChangesSetsBookingId() throws Exception {
        SearchResponse response = SearchResponse.builder()
            .searchId("s1")
            .offers(List.of())
            .incomplete(false)
            .build();
        when(bookingChangeService.searchForChange(any())).thenReturn(response);

        BookingChangeSearchRequest request = BookingChangeSearchRequest.builder()
            .bookingId("other")
            .build();

        mockMvc.perform(post("/api/v1/transfers/bookings/b1/search-changes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.searchId").value("s1"));

        ArgumentCaptor<BookingChangeSearchRequest> captor = ArgumentCaptor.forClass(BookingChangeSearchRequest.class);
        verify(bookingChangeService).searchForChange(captor.capture());
        assertThat(captor.getValue().getBookingId()).isEqualTo("b1");
    }

    @Test
    void commitChangePassesIdempotencyKeyAndSetsOldBooking() throws Exception {
        BookingChangeResponse response = BookingChangeResponse.pending("b1");
        when(bookingChangeService.commitChange(any(), eq("idem-1"))).thenReturn(response);

        BookingChangeCommitRequest request = BookingChangeCommitRequest.builder()
            .oldBookingId("other")
            .resultId("r1")
            .searchId("s1")
            .email("a@b.com")
            .phoneNumber("1")
            .countryCode("US")
            .build();

        mockMvc.perform(post("/api/v1/transfers/bookings/b1/commit-change")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "idem-1")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));

        ArgumentCaptor<BookingChangeCommitRequest> captor = ArgumentCaptor.forClass(BookingChangeCommitRequest.class);
        verify(bookingChangeService).commitChange(captor.capture(), eq("idem-1"));
        assertThat(captor.getValue().getOldBookingId()).isEqualTo("b1");
    }
}
