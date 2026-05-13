CREATE TABLE games (
    id uuid PRIMARY KEY,
    room_id uuid NOT NULL UNIQUE REFERENCES rooms (id) ON DELETE CASCADE,
    status varchar(32) NOT NULL,
    current_turn_number integer NOT NULL,
    turn_limit integer NOT NULL,
    turn_timeout_seconds integer NOT NULL,
    started_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);

CREATE TABLE game_turns (
    id uuid PRIMARY KEY,
    game_id uuid NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    player_user_id uuid NOT NULL REFERENCES users (id),
    turn_number integer NOT NULL,
    status varchar(32) NOT NULL,
    started_at timestamp with time zone,
    expires_at timestamp with time zone,
    submitted_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    UNIQUE (game_id, turn_number)
);

CREATE TABLE stories (
    id uuid PRIMARY KEY,
    game_id uuid NOT NULL UNIQUE REFERENCES games (id) ON DELETE CASCADE,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);

CREATE TABLE story_segments (
    id uuid PRIMARY KEY,
    story_id uuid NOT NULL REFERENCES stories (id) ON DELETE CASCADE,
    turn_id uuid REFERENCES game_turns (id),
    author_user_id uuid REFERENCES users (id),
    sequence_number integer NOT NULL,
    content text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    UNIQUE (story_id, sequence_number)
);

CREATE INDEX idx_games_room_id ON games (room_id);
CREATE INDEX idx_game_turns_game_id ON game_turns (game_id);
CREATE INDEX idx_game_turns_player_user_id ON game_turns (player_user_id);
CREATE INDEX idx_story_segments_story_id ON story_segments (story_id);
