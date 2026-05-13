CREATE TABLE ai_generation_attempts (
    id uuid PRIMARY KEY,
    game_id uuid REFERENCES games (id) ON DELETE CASCADE,
    turn_id uuid REFERENCES game_turns (id) ON DELETE CASCADE,
    normalized_word varchar(80) NOT NULL,
    attempt_number integer NOT NULL,
    status varchar(32) NOT NULL,
    provider varchar(64) NOT NULL,
    model varchar(128),
    prompt_tokens integer,
    completion_tokens integer,
    latency_ms bigint NOT NULL,
    failure_reason varchar(512),
    created_at timestamp with time zone NOT NULL
);

CREATE INDEX idx_ai_generation_attempts_game_id ON ai_generation_attempts (game_id);
CREATE INDEX idx_ai_generation_attempts_turn_id ON ai_generation_attempts (turn_id);
CREATE INDEX idx_ai_generation_attempts_created_at ON ai_generation_attempts (created_at);
