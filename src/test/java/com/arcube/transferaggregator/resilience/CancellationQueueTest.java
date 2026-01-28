package com.arcube.transferaggregator.resilience;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CancellationQueueTest {

    @Test
    void enqueueAndCompleteTracksPending() {
        CancellationQueue queue = new CancellationQueue();
        CancellationTask task = CancellationTask.create("b1", "SUP", "r1", "c1");

        queue.enqueue(task);
        assertThat(queue.getPending("b1")).isPresent();
        assertThat(queue.pendingCount()).isEqualTo(1);

        queue.complete("b1");
        assertThat(queue.getPending("b1")).isEmpty();
        assertThat(queue.pendingCount()).isEqualTo(0);
    }

    @Test
    void requeueIncrementsRetry() {
        CancellationQueue queue = new CancellationQueue();
        CancellationTask task = CancellationTask.create("b1", "SUP", "r1", "c1");

        queue.enqueue(task);
        queue.requeue(task);

        CancellationTask pending = queue.getPending("b1").orElseThrow();
        assertThat(pending.retryCount()).isEqualTo(1);
    }
}
