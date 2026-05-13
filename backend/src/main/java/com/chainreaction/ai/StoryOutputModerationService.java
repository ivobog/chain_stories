package com.chainreaction.ai;

import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;
import com.chainreaction.room.domain.SafetyMode;

@Service
public class StoryOutputModerationService {

    private static final Set<String> BLOCKED_TERMS = Set.of(
            "murder",
            "suicide",
            "rape",
            "slur",
            "nazi");
    private static final Set<String> FAMILY_BLOCKED_TERMS = Set.of(
            "blood",
            "violent",
            "weapon",
            "death");

    public void moderate(StoryGenerationResult result, StoryGenerationRequest request) {
        String text = """
                %s
                %s
                %s
                %s
                """.formatted(
                result.sentence(),
                result.summary(),
                result.storyDirection(),
                String.join(" ", result.tags()))
                .toLowerCase(Locale.ROOT);
        if (BLOCKED_TERMS.stream().anyMatch(text::contains)) {
            throw rejected("AI response failed output moderation.");
        }
        if (request.safetyMode() == SafetyMode.FAMILY && FAMILY_BLOCKED_TERMS.stream().anyMatch(text::contains)) {
            throw rejected("AI response failed family output moderation.");
        }
    }

    private ApiException rejected(String message) {
        return new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.BAD_GATEWAY, message);
    }
}
