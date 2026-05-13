ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_non_deleted_email
    ON users (lower(email))
    WHERE status <> 'DELETED';
