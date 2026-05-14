package com.chainreaction.ai;

import java.util.List;

import com.chainreaction.room.domain.SafetyMode;
import com.chainreaction.room.domain.WritingStyle;

public record StoryGenerationRequest(
        String normalizedWord,
        WritingStyle writingStyle,
        String language,
        SafetyMode safetyMode,
        String currentStory,
        List<PreviousWordUsage> previousUsages) {
}
