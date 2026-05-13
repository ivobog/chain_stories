package com.chainreaction.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.room.domain.SafetyMode;

class WordModerationServiceTests {

    private final WordModerationService service = new WordModerationService();

    @Test
    void normalizesSafeOneWordInput() {
        ModeratedWord word = service.moderate(" Dragon ", SafetyMode.TEEN);

        assertThat(word.original()).isEqualTo(" Dragon ");
        assertThat(word.normalized()).isEqualTo("dragon");
    }

    @Test
    void rejectsMultiWordInput() {
        assertThatThrownBy(() -> service.moderate("two words", SafetyMode.TEEN))
                .isInstanceOf(ApiException.class)
                .hasMessage("Submit exactly one word.");
    }

    @Test
    void rejectsUnsafeInput() {
        assertThatThrownBy(() -> service.moderate("murder", SafetyMode.TEEN))
                .isInstanceOf(ApiException.class)
                .hasMessage("Submitted word is not allowed for this room.");
    }
}
