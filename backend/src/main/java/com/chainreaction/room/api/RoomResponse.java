package com.chainreaction.room.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.chainreaction.room.domain.Room;
import com.chainreaction.room.domain.RoomParticipant;
import com.chainreaction.room.domain.RoomStatus;

public record RoomResponse(
        UUID roomId,
        String roomCode,
        RoomStatus status,
        UUID hostUserId,
        RoomSettingsResponse settings,
        List<RoomParticipantResponse> participants) {

    public static RoomResponse from(Room room, List<RoomParticipant> participants, Map<UUID, String> displayNames) {
        return new RoomResponse(
                room.getId(),
                room.getRoomCode(),
                room.getStatus(),
                room.getHost().getId(),
                new RoomSettingsResponse(
                        room.getWritingStyle(),
                        room.getLanguage(),
                        room.getSafetyMode(),
                        room.getMaxPlayers(),
                        room.getTurnLimit(),
                        room.getTurnTimeoutSeconds(),
                        room.getVisibility()),
                participants.stream()
                        .map(participant -> RoomParticipantResponse.from(
                                participant,
                                displayNames.getOrDefault(
                                        participant.getUser().getId(),
                                        participant.getUser().getEmail())))
                        .toList());
    }
}
