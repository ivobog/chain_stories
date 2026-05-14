package com.chainreaction.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;

class ApplicationObservationsTests {

    @Test
    void recordsObservationNameTagsAndErrors() {
        ObservationRegistry registry = ObservationRegistry.create();
        List<Observation.Context> started = new ArrayList<>();
        List<Observation.Context> stopped = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }

            @Override
            public void onStart(Observation.Context context) {
                started.add(context);
            }

            @Override
            public void onError(Observation.Context context) {
                errors.add(context.getError().getMessage());
            }

            @Override
            public void onStop(Observation.Context context) {
                stopped.add(context);
            }
        });

        ApplicationObservations observations = new ApplicationObservations(registry);

        String result = observations.observe(
                "game.submit_word",
                () -> "ok",
                KeyValue.of("writing_style", "funny"),
                KeyValue.of("language", "en"));

        assertThat(result).isEqualTo("ok");
        assertThat(started).hasSize(1);
        assertThat(started.getFirst().getName()).isEqualTo("game.submit_word");
        assertThat(started.getFirst().getLowCardinalityKeyValues()).containsExactlyInAnyOrder(
                KeyValue.of("writing_style", "funny"),
                KeyValue.of("language", "en"));
        assertThat(stopped).hasSize(1);
        assertThat(stopped.getFirst().getName()).isEqualTo("game.submit_word");
        assertThat(stopped.getFirst().getLowCardinalityKeyValues()).containsExactlyInAnyOrder(
                KeyValue.of("writing_style", "funny"),
                KeyValue.of("language", "en"));

        assertThatThrownBy(() -> observations.observe("ai.story_generation", () -> {
            throw new IllegalStateException("provider unavailable");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(errors).containsExactly("provider unavailable");
    }
}
