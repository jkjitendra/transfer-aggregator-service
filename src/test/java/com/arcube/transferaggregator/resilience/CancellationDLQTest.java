package com.arcube.transferaggregator.resilience;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CancellationDLQTest {

    @Test
    void addGetResolve() {
        CancellationDLQ dlq = new CancellationDLQ(new SimpleMeterRegistry());
        CancellationTask task = CancellationTask.create("b1", "SUP", "r1", "c1");

        dlq.add(task);
        assertThat(dlq.size()).isEqualTo(1);
        assertThat(dlq.get("b1")).isPresent();

        dlq.resolve("b1");
        assertThat(dlq.get("b1")).isEmpty();
        assertThat(dlq.size()).isEqualTo(0);
    }

    @Test
    void getAllAndResolveMissing() {
        CancellationDLQ dlq = new CancellationDLQ(new SimpleMeterRegistry());
        dlq.add(CancellationTask.create("b1", "SUP", "r1", "c1"));

        assertThat(dlq.getAll()).hasSize(1);

        dlq.resolve("missing");
        assertThat(dlq.size()).isEqualTo(1);
    }
}
