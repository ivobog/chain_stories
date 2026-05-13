package com.chainreaction.room.api;

import com.chainreaction.room.domain.RoomStatus;

public record RoomPreviewResponse(
        String roomCode,
        RoomStatus status,
        String hostDisplayName,
        RoomSettingsResponse settings,
        long activePlayers,
        boolean alreadyJoined,
        boolean canJoin) {
}
