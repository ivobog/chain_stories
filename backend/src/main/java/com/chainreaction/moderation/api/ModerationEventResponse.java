package com.chainreaction.moderation.api;

import java.time.Instant;
import java.util.UUID;

import com.chainreaction.moderation.ModerationEvent;
import com.chainreaction.moderation.ModerationEventOutcome;
import com.chainreaction.moderation.ModerationEventSource;
import com.chainreaction.room.domain.SafetyMode;

public record ModerationEventResponse(
        UUID eventId,
        UUID gameId,
        UUID roomId,
        UUID turnId,
        UUID playerUserId,
        ModerationEventSource source,
        ModerationEventOutcome outcome,
        SafetyMode safetyMode,
        String reason,
        String contentExcerpt,
        Instant createdAt) {

    public static ModerationEventResponse from(ModerationEvent event) {
        return new ModerationEventResponse(
                event.getId(),
                event.getGameId(),
                event.getRoomId(),
                event.getTurnId(),
                event.getPlayerUserId(),
                event.getSource(),
                event.getOutcome(),
                event.getSafetyMode(),
                event.getReason(),
                event.getContentExcerpt(),
                event.getCreatedAt());
    }
}
