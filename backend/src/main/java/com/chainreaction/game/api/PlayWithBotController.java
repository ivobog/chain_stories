package com.chainreaction.game.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chainreaction.common.security.CurrentUserPrincipal;
import com.chainreaction.game.service.PlayWithBotService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/games")
public class PlayWithBotController {

    private final PlayWithBotService playWithBotService;

    public PlayWithBotController(PlayWithBotService playWithBotService) {
        this.playWithBotService = playWithBotService;
    }

    @PostMapping("/play-with-bot")
    public ResponseEntity<PlayWithBotResponse> playWithBot(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @Valid @RequestBody PlayWithBotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(playWithBotService.createAndStart(principal.getUserId(), request));
    }
}
