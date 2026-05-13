package com.chainreaction.room.api;

import java.util.UUID;

import com.chainreaction.room.domain.RoomParticipantRole;
import com.chainreaction.room.domain.RoomStatus;

public record RoomSummaryResponse(
        UUID roomId,
        String roomCode,
        RoomStatus status,
        UUID hostUserId,
        String hostDisplayName,
        RoomParticipantRole myRole,
        RoomSettingsResponse settings,
        long activePlayers) {
}
