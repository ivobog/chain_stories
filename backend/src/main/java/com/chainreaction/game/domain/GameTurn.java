package com.chainreaction.game.domain;

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
@Table(name = "game_turns")
public class GameTurn {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_user_id", nullable = false)
    private User player;

    @Column(name = "turn_number", nullable = false)
    private int turnNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GameTurnStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GameTurn() {
    }

    public GameTurn(Game game, User player, int turnNumber, int timeoutSeconds) {
        this.id = UUID.randomUUID();
        this.game = game;
        this.player = player;
        this.turnNumber = turnNumber;
        this.status = GameTurnStatus.ACTIVE;
        this.startedAt = Instant.now();
        this.expiresAt = this.startedAt.plusSeconds(timeoutSeconds);
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

    public User getPlayer() {
        return player;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public GameTurnStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void submit() {
        this.status = GameTurnStatus.SUBMITTED;
        this.submittedAt = Instant.now();
    }

    public void skip() {
        this.status = GameTurnStatus.SKIPPED;
        this.submittedAt = Instant.now();
    }

    public Game getGame() {
        return game;
    }
}
