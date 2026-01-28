package com.arcube.transferaggregator.resilience;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CancellationTaskTest {

    @Test
    void withRetryIncrementsAndTracksError() {
        CancellationTask task = CancellationTask.create("b1", "SUP", "r1", "c1");
        CancellationTask retried = task.withRetry("oops");

        assertThat(retried.retryCount()).isEqualTo(1);
        assertThat(retried.lastError()).isEqualTo("oops");
        assertThat(retried.hasRetriesRemaining()).isTrue();
    }

    @Test
    void isExpiredForOldTask() {
        CancellationTask task = new CancellationTask(
            "b1", "SUP", "r1", "c1",
            Instant.now().minusSeconds(3601), 0, null
        );
        assertThat(task.isExpired()).isTrue();
    }
}
