package com.arcube.transferaggregator.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/** Span and timer boundary for database and Redis work, with fixed low-cardinality names. */
@Component
public class StorageTelemetry {
    private final ObservationRegistry observations;

    public StorageTelemetry(ObservationRegistry observations) {
        this.observations = observations;
    }

    public <T> T observe(String store, String action, Supplier<T> call) {
        return Observation.createNotStarted("transfer.storage", observations)
            .lowCardinalityKeyValue("store", store)
            .lowCardinalityKeyValue("action", action)
            .observe(call::get);
    }

    public void observe(String store, String action, Runnable call) {
        observe(store, action, () -> {
            call.run();
            return null;
        });
    }
}
