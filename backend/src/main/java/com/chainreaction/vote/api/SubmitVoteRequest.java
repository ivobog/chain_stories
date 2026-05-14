package com.chainreaction.vote.api;

import java.util.UUID;

import com.chainreaction.vote.VoteCategory;

import jakarta.validation.constraints.NotNull;

public record SubmitVoteRequest(
        @NotNull VoteCategory category,
        UUID targetUserId,
        UUID targetStorySegmentId) {
}
