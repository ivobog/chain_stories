package com.chainreaction.ai;

import java.util.List;

public record StoryGenerationResult(
        String sentence,
        String usedWord,
        String tone,
        int intensity,
        String safetyLevel,
        String summary,
        String storyDirection,
        List<String> tags,
        String model,
        int promptTokens,
        int completionTokens) {
}
