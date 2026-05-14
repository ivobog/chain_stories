package com.chainreaction.game.domain;

import java.time.Instant;
import java.util.UUID;

import com.chainreaction.room.domain.Room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "games")
public class Game {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, unique = true)
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GameStatus status;

    @Column(name = "current_turn_number", nullable = false)
    private int currentTurnNumber;

    @Column(name = "turn_limit", nullable = false)
    private int turnLimit;

    @Column(name = "turn_timeout_seconds", nullable = false)
    private int turnTimeoutSeconds;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Game() {
    }

    public Game(Room room) {
        this.id = UUID.randomUUID();
        this.room = room;
        this.status = GameStatus.ACTIVE;
        this.currentTurnNumber = 1;
        this.turnLimit = room.getTurnLimit();
        this.turnTimeoutSeconds = room.getTurnTimeoutSeconds();
        this.startedAt = Instant.now();
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

    public UUID getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public GameStatus getStatus() {
        return status;
    }

    public int getCurrentTurnNumber() {
        return currentTurnNumber;
    }

    public int getTurnLimit() {
        return turnLimit;
    }

    public int getTurnTimeoutSeconds() {
        return turnTimeoutSeconds;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void advanceToTurn(int turnNumber) {
        this.currentTurnNumber = turnNumber;
    }

    public void moveToVoting() {
        this.status = GameStatus.VOTING;
        this.completedAt = Instant.now();
    }

    public void finish() {
        this.status = GameStatus.FINISHED;
    }
}
