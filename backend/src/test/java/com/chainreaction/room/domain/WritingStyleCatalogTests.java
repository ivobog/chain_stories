package com.chainreaction.room.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class WritingStyleCatalogTests {

    @Test
    void everyStyleHasNonEmptyDisplayLabelAndGuidance() {
        for (WritingStyle style : WritingStyle.values()) {
            assertThat(style.displayLabel()).isNotBlank();
            assertThat(style.guidance()).isNotBlank();
        }
    }

    @Test
    void approvedCatalogContainsExactlySeventeenStyles() {
        assertThat(Arrays.stream(WritingStyle.values()).map(Enum::name))
                .containsExactly(
                        "FUNNY",
                        "HORROR",
                        "BATSHIT_CRAZY",
                        "DETECTIVE_NOIR",
                        "FAMILY_FRIENDLY",
                        "DARK_HUMOR",
                        "SCI_FI",
                        "ROMANCE",
                        "EPIC",
                        "CREEPY",
                        "POETIC_PROSE",
                        "HOMER",
                        "WILLIAM_SHAKESPEARE",
                        "EDGAR_ALLAN_POE",
                        "OSCAR_WILDE",
                        "NIKOLAI_GOGOL",
                        "MIGUEL_DE_CERVANTES");
    }
}
