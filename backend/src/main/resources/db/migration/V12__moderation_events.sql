CREATE TABLE moderation_events (
    id uuid PRIMARY KEY,
    game_id uuid REFERENCES games (id) ON DELETE SET NULL,
    room_id uuid REFERENCES rooms (id) ON DELETE SET NULL,
    turn_id uuid REFERENCES game_turns (id) ON DELETE SET NULL,
    player_user_id uuid REFERENCES users (id) ON DELETE SET NULL,
    source varchar(32) NOT NULL,
    outcome varchar(32) NOT NULL,
    safety_mode varchar(32) NOT NULL,
    reason varchar(512) NOT NULL,
    content_excerpt varchar(512),
    created_at timestamp with time zone NOT NULL
);

CREATE INDEX idx_moderation_events_created_at ON moderation_events (created_at);
CREATE INDEX idx_moderation_events_game_id ON moderation_events (game_id);
CREATE INDEX idx_moderation_events_room_id ON moderation_events (room_id);
CREATE INDEX idx_moderation_events_player_user_id ON moderation_events (player_user_id);
