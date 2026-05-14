package com.chainreaction.game.api;

import com.chainreaction.ai.WordSuggestionResult;
import com.chainreaction.room.domain.WritingStyle;

public record RandomWordSuggestionResponse(
        String word,
        String normalizedWord,
        String safetyLevel,
        WritingStyle writingStyle,
        String language) {

    public static RandomWordSuggestionResponse from(
            WordSuggestionResult result,
            WritingStyle writingStyle,
            String language) {
        return new RandomWordSuggestionResponse(
                result.word(),
                result.normalizedWord(),
                result.safetyLevel(),
                writingStyle,
                language);
    }
}
