package com.chainreaction.ai;

import org.springframework.stereotype.Component;

@Component
public class StoryPromptBuilder {

    public StoryGenerationPrompt build(StoryGenerationRequest request) {
        String systemPrompt = """
                You extend a shared party story by exactly one sentence.
                Return structured JSON with sentence, usedWord, tone, intensity, safetyLevel, summary, storyDirection, and tags.
                Do not rewrite earlier story text. Keep the output safe for the configured safety mode.
                Use the previous usage context to avoid repeating old jokes, images, or twists.
                """;
        String userPrompt = """
                Writing style: %s
                Language: %s
                Safety mode: %s
                Required word: %s
                Previous accepted usages for this word/style/language:
                %s
                Current story:
                %s
                """.formatted(
                request.writingStyle(),
                request.language(),
                request.safetyMode(),
                request.normalizedWord(),
                previousUsageContext(request),
                request.currentStory());
        return new StoryGenerationPrompt(systemPrompt.strip(), userPrompt.strip());
    }

    private String previousUsageContext(StoryGenerationRequest request) {
        if (request.previousUsages() == null || request.previousUsages().isEmpty()) {
            return "None yet.";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < request.previousUsages().size(); index++) {
            builder.append(index + 1)
                    .append(". ")
                    .append(request.previousUsages().get(index).generatedSentence());
            if (index < request.previousUsages().size() - 1) {
                builder.append(System.lineSeparator());
            }
        }
        return builder.toString();
    }
}
