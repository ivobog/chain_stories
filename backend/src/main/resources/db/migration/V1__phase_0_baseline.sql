-- Phase 0 baseline migration.
-- Domain tables start in Phase 1 with users and profiles.
CREATE TABLE IF NOT EXISTS schema_version_marker (
    id integer PRIMARY KEY,
    description varchar(100) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now()
);

INSERT INTO schema_version_marker (id, description)
VALUES (1, 'phase-0-baseline')
ON CONFLICT (id) DO NOTHING;
