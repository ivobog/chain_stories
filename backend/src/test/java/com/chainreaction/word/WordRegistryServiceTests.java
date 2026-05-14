package com.chainreaction.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.chainreaction.ai.PreviousWordUsage;
import com.chainreaction.room.domain.WritingStyle;

class WordRegistryServiceTests {

    private final WordRegistryEntryRepository repository = mock(WordRegistryEntryRepository.class);
    private final WordRegistryService service = new WordRegistryService(repository, 30);

    @Test
    void returnsRecentUsagesForPrompt() {
        UUID roomId = UUID.randomUUID();
        WordRegistryEntry first = entry("The word \"dragon\" made soup furious.");
        WordRegistryEntry second = entry("The word \"dragon\" opened a tiny tax office.");
        when(repository.findTop5ByRoomIdAndNormalizedWordAndWritingStyleAndLanguageAndCreatedAtAfterOrderByCreatedAtDesc(
                eq(roomId),
                eq("dragon"),
                eq(WritingStyle.FUNNY),
                eq("en"),
                any(Instant.class)))
                .thenReturn(List.of(first, second));

        List<PreviousWordUsage> usages = service.recentUsagesForPrompt(roomId, "dragon", WritingStyle.FUNNY, "en");

        assertThat(usages).containsExactly(
                new PreviousWordUsage("The word \"dragon\" made soup furious."),
                new PreviousWordUsage("The word \"dragon\" opened a tiny tax office."));
        verify(repository).findTop5ByRoomIdAndNormalizedWordAndWritingStyleAndLanguageAndCreatedAtAfterOrderByCreatedAtDesc(
                eq(roomId),
                eq("dragon"),
                eq(WritingStyle.FUNNY),
                eq("en"),
                any(Instant.class));
    }

    private WordRegistryEntry entry(String sentence) {
        return new WordRegistryEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "dragon",
                WritingStyle.FUNNY,
                "en",
                sentence);
    }
}
