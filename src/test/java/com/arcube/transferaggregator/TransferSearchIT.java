package com.arcube.transferaggregator;

import com.arcube.transferaggregator.dto.SearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "transfer.aggregator.suppliers.slow-stub.enabled=false"
})
class TransferSearchIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void searchEndpointReturnsOffers() throws Exception {
        SearchRequest request = SearchRequest.builder()
            .pickupLocation(new SearchRequest.LocationDto("A", null, null, null, null))
            .dropoffLocation(new SearchRequest.LocationDto("B", null, null, null, null))
            .numPassengers(1)
            .build();

        mockMvc.perform(post("/api/v1/transfers/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.searchId").exists())
            .andExpect(jsonPath("$.offers").isArray());
    }
}
