package com.chainreaction.ai;

public interface StoryAiProvider {

    default String providerName() {
        return "custom";
    }

    StoryGenerationResult generate(StoryGenerationPrompt prompt, StoryGenerationRequest request);
}
