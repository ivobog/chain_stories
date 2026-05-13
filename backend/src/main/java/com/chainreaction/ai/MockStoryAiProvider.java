package com.chainreaction.ai;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockStoryAiProvider implements StoryAiProvider {

    private final String model;

    public MockStoryAiProvider(@Value("${app.ai.mock.model:mock-story-v1}") String model) {
        this.model = model;
    }

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public StoryGenerationResult generate(StoryGenerationPrompt prompt, StoryGenerationRequest request) {
        String sentence = "The word \"" + request.normalizedWord() + "\" pushes the story into a stranger turn.";
        int promptTokens = estimateTokens(prompt.systemPrompt()) + estimateTokens(prompt.userPrompt());
        int completionTokens = estimateTokens(sentence);
        return new StoryGenerationResult(
                sentence,
                request.normalizedWord(),
                request.writingStyle().name(),
                2,
                request.safetyMode().name(),
                "The story takes a stranger turn.",
                "Continue the escalating chain reaction.",
                List.of("mock", request.writingStyle().name().toLowerCase()),
                model,
                promptTokens,
                completionTokens);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.split("\\s+").length);
    }
}
