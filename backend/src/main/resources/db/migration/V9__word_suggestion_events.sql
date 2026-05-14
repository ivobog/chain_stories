CREATE TABLE word_suggestion_events (
    id uuid PRIMARY KEY,
    game_id uuid NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    room_id uuid NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    turn_id uuid NOT NULL REFERENCES game_turns (id) ON DELETE CASCADE,
    player_user_id uuid REFERENCES users (id) ON DELETE SET NULL,
    suggested_word varchar(80) NOT NULL,
    normalized_word varchar(80) NOT NULL,
    writing_style varchar(64) NOT NULL,
    language varchar(16) NOT NULL,
    safety_level varchar(32) NOT NULL,
    current_story_characters integer NOT NULL,
    previous_words_count integer NOT NULL,
    created_at timestamp with time zone NOT NULL
);

CREATE INDEX idx_word_suggestion_events_game_id ON word_suggestion_events (game_id);
CREATE INDEX idx_word_suggestion_events_turn_id ON word_suggestion_events (turn_id);
CREATE INDEX idx_word_suggestion_events_player_created_at
    ON word_suggestion_events (player_user_id, created_at DESC);
