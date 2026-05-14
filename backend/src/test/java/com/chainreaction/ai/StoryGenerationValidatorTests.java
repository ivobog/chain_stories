package com.chainreaction.ai;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.room.domain.SafetyMode;
import com.chainreaction.room.domain.WritingStyle;

class StoryGenerationValidatorTests {

    private final StoryGenerationValidator validator = new StoryGenerationValidator();

    @Test
    void acceptsCompleteOutputThatUsesSubmittedWord() {
        assertThatCode(() -> validator.validate(validResult(), request(SafetyMode.TEEN)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsOutputThatIgnoresSubmittedWord() {
        StoryGenerationResult result = new StoryGenerationResult(
                "A lantern glows in the alley.",
                "lantern",
                "FUNNY",
                2,
                "TEEN",
                "A lantern appears.",
                "Keep moving.",
                List.of("mock"),
                "test-model",
                10,
                8);

        assertThatThrownBy(() -> validator.validate(result, request(SafetyMode.TEEN)))
                .isInstanceOf(ApiException.class)
                .hasMessage("AI response did not use the submitted word.");
    }

    @Test
    void rejectsNonFamilyOutputForFamilyRooms() {
        assertThatThrownBy(() -> validator.validate(validResult(), request(SafetyMode.FAMILY)))
                .isInstanceOf(ApiException.class)
                .hasMessage("AI response violates family safety mode.");
    }

    @Test
    void rejectsOutputThatRewritesFullStory() {
        StoryGenerationRequest request = new StoryGenerationRequest(
                "dragon",
                WritingStyle.FUNNY,
                "en",
                SafetyMode.TEEN,
                "The story begins.",
                List.of());
        StoryGenerationResult result = new StoryGenerationResult(
                "The story begins. The word \"dragon\" appears beside the door.",
                "dragon",
                "FUNNY",
                2,
                "TEEN",
                "A dragon appears.",
                "Keep moving.",
                List.of("mock"),
                "test-model",
                10,
                8);

        assertThatThrownBy(() -> validator.validate(result, request))
                .isInstanceOf(ApiException.class)
                .hasMessage("AI response rewrote the full story instead of adding a segment.");
    }

    private StoryGenerationResult validResult() {
        return new StoryGenerationResult(
                "The word \"dragon\" pushes the story into a stranger turn.",
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

    private StoryGenerationRequest request(SafetyMode safetyMode) {
        return new StoryGenerationRequest(
                "dragon",
                WritingStyle.FUNNY,
                "en",
                safetyMode,
                "The story begins.",
                List.of());
    }
}
