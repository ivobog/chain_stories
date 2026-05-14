package com.chainreaction.word;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chainreaction.room.domain.WritingStyle;

public interface WordRegistryEntryRepository extends JpaRepository<WordRegistryEntry, UUID> {

    List<WordRegistryEntry> findAllByGameIdOrderByCreatedAtAsc(UUID gameId);

    List<WordRegistryEntry> findTop5ByRoomIdAndNormalizedWordAndWritingStyleAndLanguageAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID roomId,
            String normalizedWord,
            WritingStyle writingStyle,
            String language,
            Instant createdAtAfter);
}
