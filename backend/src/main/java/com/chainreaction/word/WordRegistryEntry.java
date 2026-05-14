package com.chainreaction.word;

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
@Table(name = "word_registry_entries")
public class WordRegistryEntry {

    @Id
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "turn_id", nullable = false)
    private UUID turnId;

    @Column(name = "story_segment_id", nullable = false)
    private UUID storySegmentId;

    @Column(name = "player_user_id")
    private UUID playerUserId;

    @Column(name = "normalized_word", nullable = false, length = 80)
    private String normalizedWord;

    @Enumerated(EnumType.STRING)
    @Column(name = "writing_style", nullable = false, length = 64)
    private WritingStyle writingStyle;

    @Column(nullable = false, length = 16)
    private String language;

    @Column(name = "generated_sentence", nullable = false, columnDefinition = "text")
    private String generatedSentence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WordRegistryEntry() {
    }

    public WordRegistryEntry(
            UUID gameId,
            UUID roomId,
            UUID turnId,
            UUID storySegmentId,
            UUID playerUserId,
            String normalizedWord,
            WritingStyle writingStyle,
            String language,
            String generatedSentence) {
        this.id = UUID.randomUUID();
        this.gameId = gameId;
        this.roomId = roomId;
        this.turnId = turnId;
        this.storySegmentId = storySegmentId;
        this.playerUserId = playerUserId;
        this.normalizedWord = normalizedWord;
        this.writingStyle = writingStyle;
        this.language = language;
        this.generatedSentence = generatedSentence;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
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

    public UUID getStorySegmentId() {
        return storySegmentId;
    }

    public UUID getPlayerUserId() {
        return playerUserId;
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

    public String getGeneratedSentence() {
        return generatedSentence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
