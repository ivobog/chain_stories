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
@Table(name = "vote_results")
public class VoteResult {

    @Id
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private VoteCategory category;

    @Column(name = "result_rank", nullable = false)
    private int resultRank;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "target_story_segment_id")
    private UUID targetStorySegmentId;

    @Column(name = "vote_count", nullable = false)
    private int voteCount;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    protected VoteResult() {
    }

    public VoteResult(
            UUID gameId,
            VoteCategory category,
            int resultRank,
            UUID targetUserId,
            UUID targetStorySegmentId,
            long voteCount) {
        this.id = UUID.randomUUID();
        this.gameId = gameId;
        this.category = category;
        this.resultRank = resultRank;
        this.targetUserId = targetUserId;
        this.targetStorySegmentId = targetStorySegmentId;
        this.voteCount = Math.toIntExact(voteCount);
    }

    @PrePersist
    void prePersist() {
        this.calculatedAt = Instant.now();
    }

    public UUID getGameId() {
        return gameId;
    }

    public VoteCategory getCategory() {
        return category;
    }

    public int getResultRank() {
        return resultRank;
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public UUID getTargetStorySegmentId() {
        return targetStorySegmentId;
    }

    public int getVoteCount() {
        return voteCount;
    }
}
