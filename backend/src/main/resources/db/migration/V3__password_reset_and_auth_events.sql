CREATE TABLE password_reset_tokens (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash varchar(128) NOT NULL UNIQUE,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens (expires_at);

CREATE TABLE auth_events (
    id uuid PRIMARY KEY,
    user_id uuid REFERENCES users (id) ON DELETE SET NULL,
    email varchar(320),
    event_type varchar(64) NOT NULL,
    outcome varchar(32) NOT NULL,
    reason varchar(120),
    correlation_id varchar(100),
    created_at timestamp with time zone NOT NULL
);

CREATE INDEX idx_auth_events_user_id ON auth_events (user_id);
CREATE INDEX idx_auth_events_created_at ON auth_events (created_at);
