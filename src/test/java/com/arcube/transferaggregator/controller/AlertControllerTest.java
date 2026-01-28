package com.arcube.transferaggregator.controller;

import com.arcube.transferaggregator.exception.GlobalExceptionHandler;
import com.arcube.transferaggregator.observability.ErrorMetrics;
import com.arcube.transferaggregator.service.AlertingService;
import com.arcube.transferaggregator.service.AlertingService.AlertLevel;
import com.arcube.transferaggregator.service.AlertingService.AlertStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlertController.class)
@Import(GlobalExceptionHandler.class)
class AlertControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AlertingService alertingService;

    @MockBean
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
    void simulateDlqItems() throws Exception {
        when(errorMetrics.getDlqSize("main")).thenReturn(5);

        mockMvc.perform(post("/api/v1/admin/alerts/dlq/simulate")
                .param("count", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.added").value(3))
            .andExpect(jsonPath("$.newSize").value(5));

        verify(errorMetrics, times(3)).incrementDlqSize(eq("main"));
    }

    @Test
    void clearDlq() throws Exception {
        when(errorMetrics.getDlqSize("main")).thenReturn(2);

        mockMvc.perform(delete("/api/v1/admin/alerts/dlq"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cleared").value(2));

        verify(errorMetrics, times(2)).decrementDlqSize(eq("main"));
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
