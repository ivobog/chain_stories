package com.chainreaction.room.domain;

import com.chainreaction.user.domain.User;

public enum ParticipantType {
    HUMAN,
    BOT;

    public static ParticipantType from(User user) {
        return user.isBot() ? BOT : HUMAN;
    }
}
