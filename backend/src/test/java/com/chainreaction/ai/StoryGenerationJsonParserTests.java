package com.chainreaction.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.chainreaction.common.error.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;

class StoryGenerationJsonParserTests {

    private final StoryGenerationJsonParser parser = new StoryGenerationJsonParser(new ObjectMapper());

    @Test
    void parsesStructuredProviderOutput() {
        StoryGenerationResult result = parser.parse("""
                {
                  "sentence": "The word \\"dragon\\" lights up the crooked lighthouse.",
                  "usedWord": "dragon",
                  "tone": "FUNNY",
                  "intensity": 2,
                  "safetyLevel": "TEEN",
                  "summary": "A lighthouse reacts to the dragon.",
                  "storyDirection": "Keep the strange journey moving.",
                  "tags": ["lighthouse", "dragon"],
                  "extraProviderField": "ignored"
                }
                """, "real-model", 42, 17);

        assertThat(result.sentence()).isEqualTo("The word \"dragon\" lights up the crooked lighthouse.");
        assertThat(result.usedWord()).isEqualTo("dragon");
        assertThat(result.model()).isEqualTo("real-model");
        assertThat(result.promptTokens()).isEqualTo(42);
        assertThat(result.completionTokens()).isEqualTo(17);
        assertThat(result.tags()).containsExactly("lighthouse", "dragon");
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> parser.parse("not-json", "real-model", 1, 1))
                .isInstanceOf(ApiException.class)
                .hasMessage("AI response was not valid structured JSON.");
    }
}
