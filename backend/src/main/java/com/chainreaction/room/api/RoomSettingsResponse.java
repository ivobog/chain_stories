package com.chainreaction.room.api;

import com.chainreaction.room.domain.RoomVisibility;
import com.chainreaction.room.domain.SafetyMode;
import com.chainreaction.room.domain.WritingStyle;

public record RoomSettingsResponse(
        WritingStyle writingStyle,
        String language,
        SafetyMode safetyMode,
        int maxPlayers,
        int turnLimit,
        int turnTimeoutSeconds,
        RoomVisibility visibility) {
}
