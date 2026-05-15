package com.chainreaction.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.chainreaction.room.domain.SafetyMode;
import com.chainreaction.room.domain.WritingStyle;

class WordSuggestionServiceTests {

    private final WordSuggestionService service = new WordSuggestionService(
            new WordModerationService(),
            new WordSuggestionPromptBuilder());

    @Test
    void suggestsSafeStyleAwareWord() {
        WordSuggestionResult result = service.suggest(new WordSuggestionRequest(
                WritingStyle.FUNNY,
                "en",
                SafetyMode.TEEN,
                "The story begins.",
                List.of()));

        assertThat(result.word()).isIn("pickle", "kazoo", "waffle", "mustache", "banana");
        assertThat(result.normalizedWord()).isEqualTo(result.word());
        assertThat(result.safetyLevel()).isEqualTo("TEEN");
    }

    @Test
    void avoidsPreviouslyAcceptedWordsWhenPossible() {
        WordSuggestionResult result = service.suggest(new WordSuggestionRequest(
                WritingStyle.FUNNY,
                "en",
                SafetyMode.FAMILY,
                "The story begins.",
                List.of("pickle", "kazoo", "waffle", "mustache")));

        assertThat(result.normalizedWord()).isEqualTo("banana");
        assertThat(result.safetyLevel()).isEqualTo("FAMILY");
    }

    @Test
    void skipsUnsafeCandidateWords() {
        WordSuggestionService unsafeFirstService = new WordSuggestionService(
                new WordModerationService(),
                new WordSuggestionPromptBuilder(),
                Map.of(WritingStyle.FUNNY, List.of("murder", "spark")),
                List.of("lantern"));

        WordSuggestionResult result = unsafeFirstService.suggest(new WordSuggestionRequest(
                WritingStyle.FUNNY,
                "en",
                SafetyMode.TEEN,
                "The story begins.",
                List.of()));

        assertThat(result.normalizedWord()).isEqualTo("spark");
    }

    @Test
    void providesSuggestionCoverageForEveryApprovedStyle() {
        for (WritingStyle style : WritingStyle.values()) {
            WordSuggestionResult result = service.suggest(new WordSuggestionRequest(
                    style,
                    "en",
                    SafetyMode.TEEN,
                    "The story begins.",
                    List.of()));

            assertThat(result.word()).isNotBlank();
            assertThat(result.normalizedWord()).isEqualTo(result.word());
            assertThat(result.safetyLevel()).isEqualTo("TEEN");
        }
    }
}
