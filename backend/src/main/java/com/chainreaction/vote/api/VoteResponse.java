package com.chainreaction.vote.api;

import java.time.Instant;
import java.util.UUID;

import com.chainreaction.vote.Vote;
import com.chainreaction.vote.VoteCategory;

public record VoteResponse(
        UUID voteId,
        UUID gameId,
        UUID voterUserId,
        VoteCategory category,
        UUID targetUserId,
        UUID targetStorySegmentId,
        Instant createdAt) {

    public static VoteResponse from(Vote vote) {
        return new VoteResponse(
                vote.getId(),
                vote.getGameId(),
                vote.getVoterUserId(),
                vote.getCategory(),
                vote.getTargetUserId(),
                vote.getTargetStorySegmentId(),
                vote.getCreatedAt());
    }
}
