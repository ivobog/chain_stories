package com.chainreaction.ai;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.room.domain.SafetyMode;
import com.chainreaction.room.domain.WritingStyle;

class StoryOutputModerationServiceTests {

    private final StoryOutputModerationService service = new StoryOutputModerationService();

    @Test
    void acceptsSafeOutput() {
        assertThatCode(() -> service.moderate(result("The word \"dragon\" sparks a surprising parade."), request(SafetyMode.TEEN)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsBlockedTerms() {
        assertThatThrownBy(() -> service.moderate(result("The word \"dragon\" reveals a murder mystery."), request(SafetyMode.TEEN)))
                .isInstanceOf(ApiException.class)
                .hasMessage("AI response failed output moderation.");
    }

    @Test
    void rejectsFamilyModeUnsafeTerms() {
        assertThatThrownBy(() -> service.moderate(result("The word \"dragon\" drops a weapon by the gate."), request(SafetyMode.FAMILY)))
                .isInstanceOf(ApiException.class)
                .hasMessage("AI response failed family output moderation.");
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
