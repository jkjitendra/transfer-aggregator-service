package com.arcube.transferaggregator.controller;

import com.arcube.transferaggregator.dto.PricingRequest;
import com.arcube.transferaggregator.dto.PricingResponse;
import com.arcube.transferaggregator.exception.GlobalExceptionHandler;
import com.arcube.transferaggregator.service.PricingService;
import com.arcube.transferaggregator.service.PricingService.AmenityInfo;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PricingController.class)
@Import(GlobalExceptionHandler.class)
class PricingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    PricingService pricingService;

    @Test
    void postPricingReturnsResponse() throws Exception {
        PricingResponse response = PricingResponse.builder().offerId("o1").build();
        when(pricingService.calculatePrice(any())).thenReturn(response);

        PricingRequest request = PricingRequest.builder()
            .searchId("s1")
            .offerId("o1")
            .amenities(List.of("wifi"))
            .build();

        mockMvc.perform(post("/api/v1/pricing")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.offerId").value("o1"));
    }

    @Test
    void getPricingBuildsRequestFromParams() throws Exception {
        when(pricingService.calculatePrice(any())).thenReturn(PricingResponse.builder().offerId("o1").build());

        mockMvc.perform(get("/api/v1/pricing")
                .param("searchId", "s1")
                .param("offerId", "o1")
                .param("amenities", "wifi")
                .param("amenities", "baby_seats"))
            .andExpect(status().isOk());

        ArgumentCaptor<PricingRequest> captor = ArgumentCaptor.forClass(PricingRequest.class);
        verify(pricingService).calculatePrice(captor.capture());
        assertThat(captor.getValue().getAmenities()).containsExactly("wifi", "baby_seats");
    }

    @Test
    void getAmenitiesReturnsList() throws Exception {
        when(pricingService.getAvailableAmenities("o1")).thenReturn(List.of(
            new AmenityInfo("wifi", "WiFi", "desc", null, 5.0)
        ));

        mockMvc.perform(get("/api/v1/pricing/o1/amenities"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].key").value("wifi"));
    }
}
