package com.chainreaction.ai;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.chainreaction.room.domain.WritingStyle;

@Service
public class WordSuggestionAnalyticsService {

    private final WordSuggestionEventRepository repository;

    public WordSuggestionAnalyticsService(WordSuggestionEventRepository repository) {
        this.repository = repository;
    }

    public void recordSuggestion(
            UUID gameId,
            UUID roomId,
            UUID turnId,
            UUID playerUserId,
            WordSuggestionResult result,
            WritingStyle writingStyle,
            String language,
            String currentStory,
            int previousWordsCount) {
        repository.save(new WordSuggestionEvent(
                gameId,
                roomId,
                turnId,
                playerUserId,
                result,
                writingStyle,
                language,
                currentStory.length(),
                previousWordsCount));
    }
}
