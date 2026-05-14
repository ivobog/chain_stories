package com.chainreaction.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.room.domain.SafetyMode;
import com.chainreaction.room.domain.WritingStyle;

class StorySimilarityServiceTests {

    @Test
    void rejectsOutputSimilarToPreviousUsage() {
        StorySimilarityService service = new StorySimilarityService(0.78);

        assertThatThrownBy(() -> service.rejectIfTooSimilar(
                result("The word \"dragon\" made soup furious again."),
                request(List.of(new PreviousWordUsage("The word \"dragon\" made soup furious.")))))
                .isInstanceOf(ApiException.class)
                .hasMessage("AI response was too similar to previous word usage.");
    }

    @Test
    void acceptsDistinctOutput() {
        StorySimilarityService service = new StorySimilarityService(0.78);

        assertThatCode(() -> service.rejectIfTooSimilar(
                result("The word \"dragon\" convinced the moon to juggle clocks."),
                request(List.of(new PreviousWordUsage("The word \"dragon\" made soup furious.")))))
                .doesNotThrowAnyException();
    }

    @Test
    void calculatesTokenJaccardSimilarity() {
        StorySimilarityService service = new StorySimilarityService(0.78);

        assertThat(service.similarity(
                "The word dragon made soup furious again.",
                "The word dragon made soup furious."))
                .isGreaterThan(0.78);
        assertThat(service.similarity(
                "The dragon moon juggles clocks.",
                "The word dragon made soup furious."))
                .isLessThan(0.5);
    }

    private StoryGenerationResult result(String sentence) {
        return new StoryGenerationResult(
                sentence,
                "dragon",
                "FUNNY",
                2,
                "TEEN",
                "The story turns strange.",
                "Continue the escalation.",
                List.of("mock"),
                "test-model",
                10,
                8);
    }

    private StoryGenerationRequest request(List<PreviousWordUsage> usages) {
        return new StoryGenerationRequest(
                "dragon",
                WritingStyle.FUNNY,
                "en",
                SafetyMode.TEEN,
                "The story begins.",
                usages);
    }
}
