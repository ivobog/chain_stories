package com.chainreaction.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.chainreaction.room.domain.SafetyMode;
import com.chainreaction.room.domain.WritingStyle;

class MockStoryAiProviderTests {

    @Test
    void usesConfiguredModelAndProviderName() {
        MockStoryAiProvider provider = new MockStoryAiProvider("mock-test-model");

        StoryGenerationResult result = provider.generate(
                new StoryGenerationPrompt("system words", "user words"),
                new StoryGenerationRequest(
                        "dragon",
                        WritingStyle.FUNNY,
                        "en",
                        SafetyMode.TEEN,
                        "The story begins."));

        assertThat(provider.providerName()).isEqualTo("mock");
        assertThat(result.model()).isEqualTo("mock-test-model");
        assertThat(result.sentence()).contains("dragon");
        assertThat(result.promptTokens()).isPositive();
        assertThat(result.completionTokens()).isPositive();
    }
}
