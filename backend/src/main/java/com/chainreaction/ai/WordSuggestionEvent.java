package com.chainreaction.ai;

import java.time.Instant;
import java.util.UUID;

import com.chainreaction.room.domain.WritingStyle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "word_suggestion_events")
public class WordSuggestionEvent {

    @Id
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "turn_id", nullable = false)
    private UUID turnId;

    @Column(name = "player_user_id")
    private UUID playerUserId;

    @Column(name = "suggested_word", nullable = false, length = 80)
    private String suggestedWord;

    @Column(name = "normalized_word", nullable = false, length = 80)
    private String normalizedWord;

    @Enumerated(EnumType.STRING)
    @Column(name = "writing_style", nullable = false, length = 64)
    private WritingStyle writingStyle;

    @Column(nullable = false, length = 16)
    private String language;

    @Column(name = "safety_level", nullable = false, length = 32)
    private String safetyLevel;

    @Column(name = "current_story_characters", nullable = false)
    private int currentStoryCharacters;

    @Column(name = "previous_words_count", nullable = false)
    private int previousWordsCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WordSuggestionEvent() {
    }

    public WordSuggestionEvent(
            UUID gameId,
            UUID roomId,
            UUID turnId,
            UUID playerUserId,
            WordSuggestionResult result,
            WritingStyle writingStyle,
            String language,
            int currentStoryCharacters,
            int previousWordsCount) {
        this.id = UUID.randomUUID();
        this.gameId = gameId;
        this.roomId = roomId;
        this.turnId = turnId;
        this.playerUserId = playerUserId;
        this.suggestedWord = result.word();
        this.normalizedWord = result.normalizedWord();
        this.writingStyle = writingStyle;
        this.language = language;
        this.safetyLevel = result.safetyLevel();
        this.currentStoryCharacters = currentStoryCharacters;
        this.previousWordsCount = previousWordsCount;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public UUID getGameId() {
        return gameId;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getTurnId() {
        return turnId;
    }

    public UUID getPlayerUserId() {
        return playerUserId;
    }

    public String getSuggestedWord() {
        return suggestedWord;
    }

    public String getNormalizedWord() {
        return normalizedWord;
    }

    public WritingStyle getWritingStyle() {
        return writingStyle;
    }

    public String getLanguage() {
        return language;
    }

    public String getSafetyLevel() {
        return safetyLevel;
    }

    public int getCurrentStoryCharacters() {
        return currentStoryCharacters;
    }

    public int getPreviousWordsCount() {
        return previousWordsCount;
    }
}
