CREATE TABLE subscriptions (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    plan varchar(32) NOT NULL,
    status varchar(32) NOT NULL,
    provider varchar(64),
    provider_subscription_id varchar(160),
    current_period_end timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);

CREATE TABLE rooms (
    id uuid PRIMARY KEY,
    room_code varchar(12) NOT NULL UNIQUE,
    host_user_id uuid NOT NULL REFERENCES users (id),
    status varchar(32) NOT NULL,
    writing_style varchar(64) NOT NULL,
    language varchar(16) NOT NULL,
    safety_mode varchar(32) NOT NULL,
    max_players integer NOT NULL,
    turn_limit integer NOT NULL,
    turn_timeout_seconds integer NOT NULL,
    visibility varchar(32) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    closed_at timestamp with time zone
);

CREATE TABLE room_participants (
    id uuid PRIMARY KEY,
    room_id uuid NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users (id),
    role varchar(32) NOT NULL,
    status varchar(32) NOT NULL,
    joined_at timestamp with time zone NOT NULL,
    left_at timestamp with time zone,
    UNIQUE (room_id, user_id)
);

CREATE INDEX idx_rooms_host_user_id ON rooms (host_user_id);
CREATE INDEX idx_room_participants_room_id ON room_participants (room_id);
CREATE INDEX idx_room_participants_user_id ON room_participants (user_id);
