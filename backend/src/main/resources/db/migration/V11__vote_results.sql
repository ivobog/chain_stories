CREATE TABLE vote_results (
    id uuid PRIMARY KEY,
    game_id uuid NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    category varchar(64) NOT NULL,
    result_rank integer NOT NULL,
    target_user_id uuid REFERENCES users (id),
    target_story_segment_id uuid REFERENCES story_segments (id) ON DELETE CASCADE,
    vote_count integer NOT NULL,
    calculated_at timestamp with time zone NOT NULL,
    UNIQUE (game_id, category, result_rank)
);

CREATE INDEX idx_vote_results_game_id ON vote_results (game_id);
