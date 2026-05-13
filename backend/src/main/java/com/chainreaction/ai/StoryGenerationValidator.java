package com.chainreaction.ai;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;
import com.chainreaction.room.domain.SafetyMode;

@Component
public class StoryGenerationValidator {

    private static final int MAX_SENTENCE_LENGTH = 280;

    public void validate(StoryGenerationResult result, StoryGenerationRequest request) {
        if (result == null) {
            throw invalid("AI response is missing required fields.");
        }
        if (isBlank(result.sentence())
                || isBlank(result.usedWord())
                || isBlank(result.tone())
                || isBlank(result.safetyLevel())
                || isBlank(result.summary())
                || isBlank(result.storyDirection())
                || result.tags() == null
                || result.tags().isEmpty()) {
            throw invalid("AI response is missing required fields.");
        }
        if (result.sentence().length() > MAX_SENTENCE_LENGTH) {
            throw invalid("AI response is too long.");
        }
        if (!result.usedWord().equalsIgnoreCase(request.normalizedWord())
                || !result.sentence().toLowerCase().contains(request.normalizedWord().toLowerCase())) {
            throw invalid("AI response did not use the submitted word.");
        }
        if (result.intensity() < 1 || result.intensity() > 5) {
            throw invalid("AI response intensity is out of range.");
        }
        if (request.safetyMode() == SafetyMode.FAMILY && !"FAMILY".equalsIgnoreCase(result.safetyLevel())) {
            throw invalid("AI response violates family safety mode.");
        }
        if (!isBlank(request.currentStory()) && result.sentence().contains(request.currentStory())) {
            throw invalid("AI response rewrote the full story instead of adding a segment.");
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.BAD_GATEWAY, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
