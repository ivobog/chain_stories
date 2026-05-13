# API Documentation

Phase 0 placeholder for REST and WebSocket contracts.

## Implemented Phase 1 Endpoints

Authentication:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/password-reset/request`
- `POST /api/v1/auth/password-reset/confirm`

Current user:

- `GET /api/v1/me`
- `PATCH /api/v1/me/profile`
- `DELETE /api/v1/me`

Account deletion:

- Revokes active refresh tokens.
- Marks the account deleted.
- Anonymizes the stored email/profile fields so the email can be reused for a new account.

Status:

- `GET /api/v1/status`

Entitlements:

- `GET /api/v1/me/entitlements`
- `GET /api/v1/me/subscription`
- `POST /api/v1/me/subscription/mock-purchase`
- `POST /api/v1/me/subscription/cancel`

Mock subscription purchase endpoints are controlled by `SUBSCRIPTIONS_MOCK_PURCHASES_ENABLED`. They are intended for local and test flows until real store receipt validation is added. Mock purchases accept `PLUS` and `CREATOR`; `FREE` and `ADMIN` are rejected.
`GET /api/v1/me/subscription` returns the effective plan, subscription status when present, provider metadata, current period end, and effective entitlement features.

Rooms:

- `GET /api/v1/rooms`
- `POST /api/v1/rooms`
- `GET /api/v1/rooms/code/{roomCode}/preview`
- `POST /api/v1/rooms/{roomCode}/join`
- `GET /api/v1/rooms/{roomId}`
- `POST /api/v1/rooms/{roomId}/close`
- `PATCH /api/v1/rooms/{roomId}/settings`
- `POST /api/v1/rooms/{roomId}/leave`
- `POST /api/v1/rooms/{roomId}/participants/{userId}/kick`

Room responses include `displayName`, role, and status for each participant. Players can leave and rejoin open rooms; if the host leaves, hosting transfers to the earliest joined active player, or the room closes when no active players remain.
Only hosts can update lobby settings. Updates are allowed while the room is in `LOBBY`, must stay within the host's plan limit, and cannot shrink `maxPlayers` below the active participant count.
`GET /api/v1/rooms` lists the current user's active joined rooms for resume flows. Room code previews expose host display name, settings, active player count, and join availability without exposing participant emails.
Close, leave, and kick actions reject rooms that are already closed, expired, or banned. Kicks can target only active non-host participants.

Games:

- `POST /api/v1/rooms/{roomId}/games/start`
- `GET /api/v1/games/{gameId}`
- `POST /api/v1/games/{gameId}/turns/{turnId}/submit-word`
- `POST /api/v1/games/{gameId}/turns/{turnId}/skip-expired`

Only the room host can start a game. A game requires at least two active players, moves the room to `ACTIVE`, creates the first turn from join order, and persists an opening story segment placeholder.
The submit-word endpoint currently uses deterministic mock story text while the AI story loop is still pending. It accepts exactly one word, enforces current-player ownership, advances to the next active player by join order, and moves the game to `VOTING` after the configured turn limit.
The skip-expired endpoint lets an active room participant advance an expired current turn. It rejects turns that are not expired yet, marks expired turns as `SKIPPED`, and uses the same advancement and voting transition rules as submitted turns.
Game responses include lifecycle timestamps (`startedAt`, `completedAt`), `currentTurn`, ordered `turns`, ordered `storySegments`, and `fullStory`, a backend-reconstructed display string joined from the persisted segment sequence. Turn responses include `submittedAt` after a word submission or expired-turn skip.

Validation:

- Passwords must be 8 to 128 characters and include uppercase, lowercase, number, and symbol characters.
- Validation failures use `errorCode: VALIDATION_FAILED` and include `fieldErrors`.

## Planned Artifacts

- OpenAPI specification for `/api/v1`.
- WebSocket event envelope and event payload schemas.
- Standard error response examples.
- Mobile API contract notes.
