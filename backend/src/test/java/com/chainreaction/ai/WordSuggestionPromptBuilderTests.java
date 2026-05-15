package com.chainreaction.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.chainreaction.room.domain.SafetyMode;
import com.chainreaction.room.domain.WritingStyle;

class WordSuggestionPromptBuilderTests {

    private final WordSuggestionPromptBuilder builder = new WordSuggestionPromptBuilder();

    @Test
    void buildsPromptWithSuggestionContext() {
        WordSuggestionPrompt prompt = builder.build(new WordSuggestionRequest(
                WritingStyle.DETECTIVE_NOIR,
                "en",
                SafetyMode.TEEN,
                "The detective found a clue.",
                List.of("rain", "shadow")));

        assertThat(prompt.systemPrompt())
                .contains("Suggest exactly one playable word")
                .contains("Do not return a phrase");
        assertThat(prompt.userPrompt())
                .contains("Writing style: DETECTIVE_NOIR")
                .contains("Language: en")
                .contains("Safety mode: TEEN")
                .contains("rain, shadow")
                .contains("The detective found a clue.");
    }

    @Test
    void labelsEmptyPreviousWords() {
        WordSuggestionPrompt prompt = builder.build(new WordSuggestionRequest(
                WritingStyle.FUNNY,
                "en",
                SafetyMode.FAMILY,
                "The story begins.",
                List.of()));

        assertThat(prompt.userPrompt()).contains("None yet.");
    }
}
