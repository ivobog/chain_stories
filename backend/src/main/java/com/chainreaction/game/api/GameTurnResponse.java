package com.chainreaction.game.api;

import java.time.Instant;
import java.util.UUID;

import com.chainreaction.game.domain.GameTurn;
import com.chainreaction.game.domain.GameTurnStatus;

public record GameTurnResponse(
        UUID turnId,
        int turnNumber,
        UUID playerUserId,
        GameTurnStatus status,
        Instant startedAt,
        Instant expiresAt,
        Instant submittedAt) {

    public static GameTurnResponse from(GameTurn turn) {
        return new GameTurnResponse(
                turn.getId(),
                turn.getTurnNumber(),
                turn.getPlayer().getId(),
                turn.getStatus(),
                turn.getStartedAt(),
                turn.getExpiresAt(),
                turn.getSubmittedAt());
    }
}
