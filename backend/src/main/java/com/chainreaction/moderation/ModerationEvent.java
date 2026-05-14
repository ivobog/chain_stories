package com.chainreaction.moderation;

import java.time.Instant;
import java.util.UUID;

import com.chainreaction.room.domain.SafetyMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "moderation_events")
public class ModerationEvent {

    @Id
    private UUID id;

    @Column(name = "game_id")
    private UUID gameId;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "turn_id")
    private UUID turnId;

    @Column(name = "player_user_id")
    private UUID playerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ModerationEventSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ModerationEventOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "safety_mode", nullable = false, length = 32)
    private SafetyMode safetyMode;

    @Column(nullable = false, length = 512)
    private String reason;

    @Column(name = "content_excerpt", length = 512)
    private String contentExcerpt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ModerationEvent() {
    }

    public ModerationEvent(
            UUID gameId,
            UUID roomId,
            UUID turnId,
            UUID playerUserId,
            ModerationEventSource source,
            ModerationEventOutcome outcome,
            SafetyMode safetyMode,
            String reason,
            String contentExcerpt) {
        this.id = UUID.randomUUID();
        this.gameId = gameId;
        this.roomId = roomId;
        this.turnId = turnId;
        this.playerUserId = playerUserId;
        this.source = source;
        this.outcome = outcome;
        this.safetyMode = safetyMode;
        this.reason = truncate(reason);
        this.contentExcerpt = contentExcerpt == null ? null : truncate(contentExcerpt);
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

    public UUID getPlayerUserId() {
        return playerUserId;
    }

    public ModerationEventSource getSource() {
        return source;
    }

    public ModerationEventOutcome getOutcome() {
        return outcome;
    }

    public SafetyMode getSafetyMode() {
        return safetyMode;
    }

    public String getReason() {
        return reason;
    }

    public String getContentExcerpt() {
        return contentExcerpt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private String truncate(String value) {
        return value.substring(0, Math.min(512, value.length()));
    }
}
