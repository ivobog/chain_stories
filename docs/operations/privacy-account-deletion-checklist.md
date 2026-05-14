# Privacy And Account Deletion Checklist

Use this checklist before private beta and after any schema change that stores user-linked data.

## Current Deletion Behavior

`DELETE /api/v1/me` performs a soft delete:

- `users.status` becomes `DELETED`.
- `users.email` is replaced with `deleted+{userId}@deleted.local`.
- `users.password_hash` is replaced with `deleted`.
- `user_profiles.display_name` becomes `Deleted user`.
- `user_profiles.avatar_url` is cleared.
- `user_profiles.favorite_style` is cleared.
- Active refresh tokens are revoked.
- An `auth_events` row records `ACCOUNT_DELETED` against the original email for audit context.

This preserves referential integrity for rooms, games, turns, stories, votes, word registry rows, moderation events, and AI attempt logs while removing direct profile identifiers.

## Manual Verification

1. Register a user and save the access/refresh tokens.
2. Update profile with display name, avatar URL, and favorite style.
3. Create or join a room and submit at least one turn if testing data retention.
4. Call `DELETE /api/v1/me`.
5. Confirm `GET /api/v1/me` with the old access token returns unauthorized.
6. Confirm `POST /api/v1/auth/refresh` with the old refresh token returns `INVALID_REFRESH_TOKEN`.
7. Confirm a new account can register with the original email.
8. Confirm the deleted row no longer contains the original email, avatar URL, or favorite style.
9. Confirm existing stories and vote results still render with a generic deleted-user display name.

## Data Review

Review these tables when adding or changing user-linked fields:

- `users`
- `user_profiles`
- `refresh_tokens`
- `auth_events`
- `rooms`
- `room_participants`
- `games`
- `game_turns`
- `stories`
- `story_segments`
- `ai_generation_attempts`
- `word_registry_entries`
- `word_suggestion_events`
- `moderation_events`
- `votes`
- `vote_results`

## Retention Notes

- Keep inactive word registry rows only as long as needed for beta debugging and tuning.
- Archive only anonymized aggregate analytics unless a privacy review approves row-level exports.
- Treat generated story text and moderation excerpts as user-associated content even when profile identifiers are anonymized.
