package com.chainreaction.vote.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chainreaction.common.security.CurrentUserPrincipal;
import com.chainreaction.vote.VoteService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/games/{gameId}/votes")
public class VoteController {

    private final VoteService voteService;

    public VoteController(VoteService voteService) {
        this.voteService = voteService;
    }

    @PostMapping
    public VoteResponse submitVote(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID gameId,
            @Valid @RequestBody SubmitVoteRequest request) {
        return voteService.submitVote(principal.getUserId(), gameId, request);
    }

    @GetMapping("/results")
    public VoteResultsResponse results(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID gameId) {
        return voteService.results(principal.getUserId(), gameId);
    }
}
