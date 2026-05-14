package com.chainreaction.observability;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@Component
public class ApplicationObservations {

    private final ObservationRegistry observationRegistry;

    public ApplicationObservations(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    public <T> T observe(String name, Supplier<T> operation, KeyValue... lowCardinalityKeyValues) {
        Observation observation = Observation.createNotStarted(name, observationRegistry);
        for (KeyValue keyValue : lowCardinalityKeyValues) {
            observation.lowCardinalityKeyValue(keyValue);
        }

        observation.start();
        try (Observation.Scope ignored = observation.openScope()) {
            return operation.get();
        } catch (RuntimeException exception) {
            observation.error(exception);
            throw exception;
        } finally {
            observation.stop();
        }
    }
}
