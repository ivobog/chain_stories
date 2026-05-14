package com.chainreaction.vote;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "votes")
public class Vote {

    @Id
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "voter_user_id", nullable = false)
    private UUID voterUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private VoteCategory category;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "target_story_segment_id")
    private UUID targetStorySegmentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Vote() {
    }

    public Vote(UUID gameId, UUID voterUserId, VoteCategory category, UUID targetUserId, UUID targetStorySegmentId) {
        this.id = UUID.randomUUID();
        this.gameId = gameId;
        this.voterUserId = voterUserId;
        this.category = category;
        this.targetUserId = targetUserId;
        this.targetStorySegmentId = targetStorySegmentId;
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

    public UUID getVoterUserId() {
        return voterUserId;
    }

    public VoteCategory getCategory() {
        return category;
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public UUID getTargetStorySegmentId() {
        return targetStorySegmentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
