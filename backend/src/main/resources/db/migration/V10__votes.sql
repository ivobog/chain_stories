CREATE TABLE votes (
    id uuid PRIMARY KEY,
    game_id uuid NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    voter_user_id uuid NOT NULL REFERENCES users (id),
    category varchar(64) NOT NULL,
    target_user_id uuid REFERENCES users (id),
    target_story_segment_id uuid REFERENCES story_segments (id) ON DELETE CASCADE,
    created_at timestamp with time zone NOT NULL,
    UNIQUE (game_id, voter_user_id, category)
);

CREATE INDEX idx_votes_game_id ON votes (game_id);
CREATE INDEX idx_votes_target_user_id ON votes (target_user_id);
CREATE INDEX idx_votes_target_story_segment_id ON votes (target_story_segment_id);
