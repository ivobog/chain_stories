package com.chainreaction.game.api;

import com.chainreaction.room.api.RoomResponse;

public record PlayWithBotResponse(
        RoomResponse room,
        GameResponse game) {
}
