package com.chainreaction.vote.api;

import java.util.List;

import com.chainreaction.vote.VoteCategory;

public record VoteCategoryResultResponse(
        VoteCategory category,
        List<VoteTargetResultResponse> results) {
}
