package com.chainreaction.realtime.api;

import java.time.Instant;
import java.util.UUID;

public record RealtimeEvent(
        RealtimeEventType type,
        UUID roomId,
        UUID gameId,
        Object payload,
        Instant occurredAt) {

    public static RealtimeEvent room(RealtimeEventType type, UUID roomId, Object payload) {
        return new RealtimeEvent(type, roomId, null, payload, Instant.now());
    }

    public static RealtimeEvent game(RealtimeEventType type, UUID roomId, UUID gameId, Object payload) {
        return new RealtimeEvent(type, roomId, gameId, payload, Instant.now());
    }

    public static RealtimeEvent user(RealtimeEventType type, Object payload) {
        return new RealtimeEvent(type, null, null, payload, Instant.now());
    }
}
