package com.arcube.transferaggregator.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorMetricsTest {

    @Test
    void recordsErrorsAndDlqSize() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ErrorMetrics metrics = new ErrorMetrics(registry);
        metrics.init();

        metrics.recordSearchError("S1", "timeout");
        metrics.recordBookingError("S1", "failed");
        metrics.recordCancellationError("S2", "failed");
        metrics.recordPollingError("search-1", "timeout");

        assertThat(metrics.getSearchErrorCount()).isEqualTo(1.0);
        assertThat(metrics.getBookingErrorCount()).isEqualTo(1.0);
        assertThat(metrics.getCancellationErrorCount()).isEqualTo(1.0);

        metrics.incrementDlqSize("main");
        metrics.incrementDlqSize("main");
        assertThat(metrics.getDlqSize("main")).isEqualTo(2);

        metrics.decrementDlqSize("main");
        assertThat(metrics.getDlqSize("main")).isEqualTo(1);
    }

    @Test
    void handlesUnknownQueueAndZeroSizeDecrement() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ErrorMetrics metrics = new ErrorMetrics(registry);
        metrics.init();

        metrics.decrementDlqSize("missing");
        assertThat(metrics.getDlqSize("missing")).isEqualTo(0);

        metrics.decrementDlqSize("main");
        assertThat(metrics.getDlqSize("main")).isEqualTo(0);

        metrics.incrementDlqSize("secondary");
        assertThat(metrics.getDlqSize("secondary")).isEqualTo(1);
    }
}
