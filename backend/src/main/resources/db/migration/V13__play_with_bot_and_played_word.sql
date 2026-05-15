ALTER TABLE users
    ADD COLUMN account_type varchar(16) NOT NULL DEFAULT 'HUMAN';

ALTER TABLE rooms
    ADD COLUMN game_mode varchar(32) NOT NULL DEFAULT 'MULTIPLAYER';

ALTER TABLE story_segments
    ADD COLUMN played_word varchar(40),
    ADD COLUMN played_word_normalized varchar(80);

CREATE INDEX idx_users_account_type ON users (account_type);
CREATE INDEX idx_rooms_game_mode ON rooms (game_mode);
CREATE INDEX idx_story_segments_played_word_normalized
    ON story_segments (played_word_normalized);
