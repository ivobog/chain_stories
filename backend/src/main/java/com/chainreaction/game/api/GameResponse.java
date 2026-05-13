package com.chainreaction.game.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.chainreaction.game.domain.Game;
import com.chainreaction.game.domain.GameStatus;

public record GameResponse(
        UUID gameId,
        UUID roomId,
        GameStatus status,
        int currentTurnNumber,
        int turnLimit,
        int turnTimeoutSeconds,
        Instant startedAt,
        Instant completedAt,
        GameTurnResponse currentTurn,
        List<UUID> turnOrder,
        List<GameTurnResponse> turns,
        String fullStory,
        List<StorySegmentResponse> storySegments) {

    public static GameResponse from(
            Game game,
            GameTurnResponse currentTurn,
            List<UUID> turnOrder,
            List<GameTurnResponse> turns,
            List<StorySegmentResponse> storySegments) {
        return new GameResponse(
                game.getId(),
                game.getRoom().getId(),
                game.getStatus(),
                game.getCurrentTurnNumber(),
                game.getTurnLimit(),
                game.getTurnTimeoutSeconds(),
                game.getStartedAt(),
                game.getCompletedAt(),
                currentTurn,
                turnOrder,
                turns,
                fullStory(storySegments),
                storySegments);
    }

    private static String fullStory(List<StorySegmentResponse> storySegments) {
        return storySegments.stream()
                .map(StorySegmentResponse::content)
                .filter(content -> content != null && !content.isBlank())
                .reduce((first, second) -> first + "\n\n" + second)
                .orElse("");
    }
}
