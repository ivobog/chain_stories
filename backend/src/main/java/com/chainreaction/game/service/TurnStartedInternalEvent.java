package com.chainreaction.game.service;

import java.util.UUID;

public record TurnStartedInternalEvent(
        UUID gameId,
        UUID turnId) {
}
