package com.chainreaction.ai;

import org.springframework.stereotype.Component;

@Component
public class StoryPromptBuilder {

    public StoryGenerationPrompt build(StoryGenerationRequest request) {
        String systemPrompt = """
                You extend a shared party story by exactly one sentence.
                Return structured JSON with sentence, usedWord, tone, intensity, safetyLevel, summary, storyDirection, and tags.
                Do not rewrite earlier story text. Keep the output safe for the configured safety mode.
                """;
        String userPrompt = """
                Writing style: %s
                Language: %s
                Safety mode: %s
                Required word: %s
                Current story:
                %s
                """.formatted(
                request.writingStyle(),
                request.language(),
                request.safetyMode(),
                request.normalizedWord(),
                request.currentStory());
        return new StoryGenerationPrompt(systemPrompt.strip(), userPrompt.strip());
    }
}
