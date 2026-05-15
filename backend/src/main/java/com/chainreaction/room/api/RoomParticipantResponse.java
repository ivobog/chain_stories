package com.chainreaction.room.api;

import java.util.UUID;

import com.chainreaction.room.domain.RoomParticipant;
import com.chainreaction.room.domain.ParticipantType;
import com.chainreaction.room.domain.RoomParticipantRole;
import com.chainreaction.room.domain.RoomParticipantStatus;

public record RoomParticipantResponse(
        UUID userId,
        String displayName,
        ParticipantType participantType,
        RoomParticipantRole role,
        RoomParticipantStatus status) {

    public static RoomParticipantResponse from(RoomParticipant participant, String displayName) {
        return new RoomParticipantResponse(
                participant.getUser().getId(),
                displayName,
                ParticipantType.from(participant.getUser()),
                participant.getRole(),
                participant.getStatus());
    }
}
