CREATE TABLE word_registry_entries (
    id uuid PRIMARY KEY,
    game_id uuid NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    room_id uuid NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    turn_id uuid NOT NULL REFERENCES game_turns (id) ON DELETE CASCADE,
    story_segment_id uuid NOT NULL UNIQUE REFERENCES story_segments (id) ON DELETE CASCADE,
    player_user_id uuid REFERENCES users (id) ON DELETE SET NULL,
    normalized_word varchar(80) NOT NULL,
    writing_style varchar(64) NOT NULL,
    language varchar(16) NOT NULL,
    generated_sentence text NOT NULL,
    created_at timestamp with time zone NOT NULL
);

CREATE INDEX idx_word_registry_entries_game_id ON word_registry_entries (game_id);
CREATE INDEX idx_word_registry_entries_room_id ON word_registry_entries (room_id);
CREATE INDEX idx_word_registry_entries_word_style_language_created_at
    ON word_registry_entries (normalized_word, writing_style, language, created_at DESC);
