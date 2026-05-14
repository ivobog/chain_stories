package com.chainreaction.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.chainreaction.room.domain.SafetyMode;
import com.chainreaction.room.domain.WritingStyle;

class StoryPromptBuilderTests {

    private final StoryPromptBuilder promptBuilder = new StoryPromptBuilder();

    @Test
    void includesPreviousUsageContext() {
        StoryGenerationPrompt prompt = promptBuilder.build(new StoryGenerationRequest(
                "dragon",
                WritingStyle.FUNNY,
                "en",
                SafetyMode.TEEN,
                "The story begins.",
                List.of(
                        new PreviousWordUsage("The word \"dragon\" made soup furious."),
                        new PreviousWordUsage("The word \"dragon\" opened a tiny tax office."))));

        assertThat(prompt.userPrompt()).contains("Previous accepted usages for this word/style/language:");
        assertThat(prompt.userPrompt()).contains("1. The word \"dragon\" made soup furious.");
        assertThat(prompt.userPrompt()).contains("2. The word \"dragon\" opened a tiny tax office.");
    }

    @Test
    void marksPreviousUsageContextEmptyWhenNoMemoryExists() {
        StoryGenerationPrompt prompt = promptBuilder.build(new StoryGenerationRequest(
                "dragon",
                WritingStyle.FUNNY,
                "en",
                SafetyMode.TEEN,
                "The story begins.",
                List.of()));

        assertThat(prompt.userPrompt()).contains("None yet.");
    }
}
