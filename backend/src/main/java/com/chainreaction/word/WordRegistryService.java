package com.chainreaction.word;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.chainreaction.ai.PreviousWordUsage;
import com.chainreaction.ai.StoryGenerationResult;
import com.chainreaction.game.domain.Game;
import com.chainreaction.game.domain.GameTurn;
import com.chainreaction.game.domain.StorySegment;
import com.chainreaction.room.domain.WritingStyle;

@Service
public class WordRegistryService {

    private final WordRegistryEntryRepository repository;
    private final long recentWindowDays;

    public WordRegistryService(
            WordRegistryEntryRepository repository,
            @Value("${app.word-registry.recent-window-days:30}") long recentWindowDays) {
        this.repository = repository;
        this.recentWindowDays = Math.max(1, recentWindowDays);
    }

    public void recordAcceptedUsage(
            Game game,
            GameTurn turn,
            StorySegment storySegment,
            StoryGenerationResult generation) {
        repository.save(new WordRegistryEntry(
                game.getId(),
                game.getRoom().getId(),
                turn.getId(),
                storySegment.getId(),
                turn.getPlayer().getId(),
                generation.usedWord().trim().toLowerCase(Locale.ROOT),
                game.getRoom().getWritingStyle(),
                game.getRoom().getLanguage(),
                generation.sentence()));
    }

    public List<PreviousWordUsage> recentUsagesForPrompt(
            UUID roomId,
            String normalizedWord,
            WritingStyle writingStyle,
            String language) {
        Instant cutoff = Instant.now().minus(Duration.ofDays(recentWindowDays));
        return repository.findTop5ByRoomIdAndNormalizedWordAndWritingStyleAndLanguageAndCreatedAtAfterOrderByCreatedAtDesc(
                        roomId,
                        normalizedWord,
                        writingStyle,
                        language,
                        cutoff)
                .stream()
                .map(entry -> new PreviousWordUsage(entry.getGeneratedSentence()))
                .toList();
    }

    public List<String> acceptedWordsForGame(UUID gameId) {
        return repository.findAllByGameIdOrderByCreatedAtAsc(gameId)
                .stream()
                .map(WordRegistryEntry::getNormalizedWord)
                .toList();
    }
}
