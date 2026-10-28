package com.arcube.transferaggregator.controller;

import com.arcube.transferaggregator.exception.GlobalExceptionHandler;
import com.arcube.transferaggregator.observability.ErrorMetrics;
import com.arcube.transferaggregator.service.AlertingService;
import com.arcube.transferaggregator.service.AlertingService.AlertLevel;
import com.arcube.transferaggregator.service.AlertingService.AlertStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static com.arcube.transferaggregator.JsonPathMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlertController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AlertControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AlertingService alertingService;

    @MockitoBean
    ErrorMetrics errorMetrics;

    @Test
    void getDlqStatus() throws Exception {
        when(alertingService.getDlqStatus()).thenReturn(
            new AlertStatus(AlertLevel.OK, 0, 10, 50));

        mockMvc.perform(get("/api/v1/admin/alerts/dlq"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.level").value("OK"));
    }

    @Test
    void getErrorMetrics() throws Exception {
        when(errorMetrics.getSearchErrorCount()).thenReturn(1.0);
        when(errorMetrics.getBookingErrorCount()).thenReturn(2.0);
        when(errorMetrics.getCancellationErrorCount()).thenReturn(3.0);
        when(errorMetrics.getTotalDlqSize()).thenReturn(4);

        mockMvc.perform(get("/api/v1/admin/alerts/errors"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.searchErrors").value(1.0))
            .andExpect(jsonPath("$.bookingErrors").value(2.0))
            .andExpect(jsonPath("$.cancellationErrors").value(3.0))
            .andExpect(jsonPath("$.totalDlqSize").value(4));
    }

    @Test
    void triggerCheck() throws Exception {
        when(alertingService.getDlqStatus()).thenReturn(
            new AlertStatus(AlertLevel.WARNING, 12, 10, 50));

        mockMvc.perform(post("/api/v1/admin/alerts/check"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("check completed"))
            .andExpect(jsonPath("$.dlqStatus.level").value("WARNING"));

        verify(alertingService).checkAlerts();
    }
}
