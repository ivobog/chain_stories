package com.chainreaction.room.domain;

import java.time.Instant;
import java.util.UUID;

import com.chainreaction.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    private UUID id;

    @Column(name = "room_code", nullable = false, unique = true, length = 12)
    private String roomCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_user_id", nullable = false)
    private User host;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RoomStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "writing_style", nullable = false, length = 64)
    private WritingStyle writingStyle;

    @Column(nullable = false, length = 16)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "safety_mode", nullable = false, length = 32)
    private SafetyMode safetyMode;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    @Column(name = "turn_limit", nullable = false)
    private int turnLimit;

    @Column(name = "turn_timeout_seconds", nullable = false)
    private int turnTimeoutSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RoomVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_mode", nullable = false, length = 32)
    private GameMode gameMode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected Room() {
    }

    public Room(
            String roomCode,
            User host,
            WritingStyle writingStyle,
            String language,
            SafetyMode safetyMode,
            int maxPlayers,
            int turnLimit,
            int turnTimeoutSeconds,
            RoomVisibility visibility) {
        this.id = UUID.randomUUID();
        this.roomCode = roomCode;
        this.host = host;
        this.status = RoomStatus.LOBBY;
        this.writingStyle = writingStyle;
        this.language = language;
        this.safetyMode = safetyMode;
        this.maxPlayers = maxPlayers;
        this.turnLimit = turnLimit;
        this.turnTimeoutSeconds = turnTimeoutSeconds;
        this.visibility = visibility;
        this.gameMode = GameMode.MULTIPLAYER;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public void close() {
        this.status = RoomStatus.CLOSED;
        this.closedAt = Instant.now();
    }

    public void startGame() {
        this.status = RoomStatus.ACTIVE;
    }

    public void transferHost(User host) {
        this.host = host;
    }

    public void updateSettings(
            WritingStyle writingStyle,
            String language,
            SafetyMode safetyMode,
            int maxPlayers,
            int turnLimit,
            int turnTimeoutSeconds,
            RoomVisibility visibility) {
        this.writingStyle = writingStyle;
        this.language = language;
        this.safetyMode = safetyMode;
        this.maxPlayers = maxPlayers;
        this.turnLimit = turnLimit;
        this.turnTimeoutSeconds = turnTimeoutSeconds;
        this.visibility = visibility;
    }

    public UUID getId() {
        return id;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public User getHost() {
        return host;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public WritingStyle getWritingStyle() {
        return writingStyle;
    }

    public String getLanguage() {
        return language;
    }

    public SafetyMode getSafetyMode() {
        return safetyMode;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getTurnLimit() {
        return turnLimit;
    }

    public int getTurnTimeoutSeconds() {
        return turnTimeoutSeconds;
    }

    public RoomVisibility getVisibility() {
        return visibility;
    }

    public GameMode getGameMode() {
        return gameMode;
    }
}
