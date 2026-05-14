package com.chainreaction.vote.api;

import java.util.UUID;

public record VoteTargetResultResponse(
        UUID targetUserId,
        UUID targetStorySegmentId,
        long voteCount) {
}
