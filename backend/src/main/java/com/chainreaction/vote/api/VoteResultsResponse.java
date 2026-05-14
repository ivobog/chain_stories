package com.chainreaction.vote.api;

import java.util.List;
import java.util.UUID;

public record VoteResultsResponse(
        UUID gameId,
        List<VoteCategoryResultResponse> categories) {
}
