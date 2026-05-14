package com.chainreaction.game.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chainreaction.common.security.CurrentUserPrincipal;
import com.chainreaction.game.service.GameService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/rooms/{roomId}/games/start")
    public GameResponse startGame(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID roomId) {
        return gameService.startGame(principal.getUserId(), roomId);
    }

    @GetMapping("/games/{gameId}")
    public GameResponse getGame(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID gameId) {
        return gameService.getGame(principal.getUserId(), gameId);
    }

    @GetMapping("/rooms/{roomId}/game")
    public GameResponse getRoomGame(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID roomId) {
        return gameService.getRoomGame(principal.getUserId(), roomId);
    }

    @PostMapping("/games/{gameId}/turns/{turnId}/submit-word")
    public GameResponse submitWord(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID gameId,
            @PathVariable UUID turnId,
            @Valid @RequestBody SubmitWordRequest request) {
        return gameService.submitWord(principal.getUserId(), gameId, turnId, request);
    }

    @PostMapping("/games/{gameId}/turns/{turnId}/skip-expired")
    public GameResponse skipExpiredTurn(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID gameId,
            @PathVariable UUID turnId) {
        return gameService.skipExpiredTurn(principal.getUserId(), gameId, turnId);
    }

    @PostMapping("/games/{gameId}/random-word")
    public RandomWordSuggestionResponse randomWord(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID gameId) {
        return gameService.randomWord(principal.getUserId(), gameId);
    }
}
