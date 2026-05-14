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
- `POST /api/v1/games/{gameId}/random-word`
- `POST /api/v1/games/{gameId}/votes`
- `GET /api/v1/games/{gameId}/votes/results`

Only the room host can start a game. A game requires at least two active players, moves the room to `ACTIVE`, creates the first turn from join order, and persists an opening story segment placeholder.
The submit-word endpoint currently uses the Phase 5 AI provider selected by `AI_PROVIDER`; `mock` is the default provider and uses `AI_MOCK_MODEL` for its reported model name. Set `AI_PROVIDER=openai` with `OPENAI_API_KEY` to call the OpenAI Responses API through the same provider-neutral pipeline. OpenAI calls use configurable `AI_OPENAI_CONNECT_TIMEOUT` and `AI_OPENAI_READ_TIMEOUT` limits. It accepts exactly one moderated word, enforces current-player ownership, retries invalid provider output up to `AI_GENERATION_MAX_ATTEMPTS`, validates structured AI output, runs output moderation, stores the accepted sentence, advances to the next active player by join order, and moves the game to `VOTING` after the configured turn limit.
Provider integrations should return structured JSON with `sentence`, `usedWord`, `tone`, `intensity`, `safetyLevel`, `summary`, `storyDirection`, and `tags`; the backend maps this into the internal generation result before validation and moderation.
Each provider attempt is recorded in `ai_generation_attempts` with game/turn ids, normalized word, attempt number, provider/model, token counts, latency, status, and failure reason for backend observability.
AI generation attempts also emit Micrometer metrics: `ai_generation_attempts_total`, `ai_generation_attempt_duration`, and `ai_generation_failures_total` for exhausted retries. Phase 6 anti-repetition also emits `word_similarity_rejections_total` with provider, writing style, and language tags.
If generation exhausts its retry budget, clients receive `errorCode: AI_GENERATION_FAILED` with a generic retry message; raw provider or validation details stay in backend telemetry.
Accepted generated segments are also recorded in the Phase 6 word registry with normalized word, room/game/turn/segment ids, player id, style, language, generated sentence, and timestamp. Rejected submissions do not create registry entries. Future submissions include recent accepted usages for the same room/word/style/language in the AI prompt as anti-repetition context; the lookup window is controlled by `WORD_REGISTRY_RECENT_WINDOW_DAYS`. Generated output that is too similar to prior room usage is rejected and retried using `WORD_REGISTRY_SIMILARITY_THRESHOLD`. Rows outside the active prompt-memory window are retained during private beta for debugging/tuning, with the pruning strategy documented in `docs/operations/word-registry-retention.md`.
The random-word endpoint is available only to the current active turn player. It returns one moderated suggestion with `word`, `normalizedWord`, `safetyLevel`, `writingStyle`, and `language`; suggestions are hints only, and the eventual submit-word request is still validated by the normal backend path. The suggestion prompt contract includes room style, language, safety mode, previous accepted words, and current story context. Successful suggestions are recorded in `word_suggestion_events` for analytics and tuning. Requests are rate-limited per player/game using `AI_SUGGESTION_RATE_LIMIT_PER_WINDOW` and `AI_SUGGESTION_RATE_LIMIT_WINDOW_SECONDS`; exceeded limits return `errorCode: RATE_LIMITED`.
The vote endpoint is available only when the game status is `VOTING`. Active participants can submit one vote per category. `BEST_SABOTAGE` and `MVP_PLAYER` require `targetUserId`; `FUNNIEST_WORD`, `WEIRDEST_TWIST`, and `BEST_AI_SENTENCE` require `targetStorySegmentId`. Duplicate category votes return `errorCode: DUPLICATE_VOTE`. Vote results return all categories with ranked target ids and vote counts while voting is open and after the game is finished. Accepted votes refresh the persisted result projection and broadcast `VOTE_RESULTS_UPDATED`. When every active participant has voted in every category, the game moves to `FINISHED` and broadcasts `GAME_FINISHED`.
The skip-expired endpoint lets an active room participant advance an expired current turn. It rejects turns that are not expired yet, marks expired turns as `SKIPPED`, and uses the same advancement and voting transition rules as submitted turns.
Game responses include lifecycle timestamps (`startedAt`, `completedAt`), `currentTurn`, ordered `turns`, ordered `storySegments`, and `fullStory`, a backend-reconstructed display string joined from the persisted segment sequence. Turn responses include `submittedAt` after a word submission or expired-turn skip.

WebSocket:

- STOMP endpoint: `/ws/game`
- Room topic: `/topic/rooms/{roomId}`
- User queue: `/user/queue/events`

WebSocket clients authenticate by sending `Authorization: Bearer <accessToken>` as a STOMP `CONNECT` native header. The authenticated principal is retained on the WebSocket session for later subscription authorization. Room and game REST mutations publish events to the room topic after successful transaction commit using a shared event envelope with `type`, `roomId`, optional `gameId`, `payload`, and `occurredAt`.
Subscriptions to `/topic/rooms/{roomId}` require the connected user to be an active participant in that room.
Subscriptions to `/user/queue/events` require an authenticated WebSocket user and are reserved for user-specific events.
Currently published event types include `PLAYER_JOINED`, `PLAYER_LEFT`, `PLAYER_KICKED`, `ROOM_CLOSED`, `GAME_STARTED`, `TURN_STARTED`, `WORD_SUBMITTED`, `AI_GENERATION_STARTED`, `STORY_SEGMENT_ADDED`, `TURN_SKIPPED`, `VOTING_STARTED`, `VOTE_RESULTS_UPDATED`, and `GAME_FINISHED`. Kicked participants also receive a private `PLAYER_KICKED` event on `/user/queue/events`.
Clients that reconnect should resubscribe to the room topic and call `GET /api/v1/games/{gameId}` to recover the full current game, turn, and story state.

Mobile client notes:

- `mobile/src/api/client.ts` wraps auth, room listing, room preview, room create/join/close/settings/leave/kick, game start/recovery, and word submission.
- `mobile/src/api/realtime.ts` wraps STOMP connection setup, room-topic subscriptions, and user-queue subscriptions.

Validation:

- Passwords must be 8 to 128 characters and include uppercase, lowercase, number, and symbol characters.
- Validation failures use `errorCode: VALIDATION_FAILED` and include `fieldErrors`.

## Planned Artifacts

- OpenAPI specification for `/api/v1`.
- WebSocket event envelope and event payload schemas.
- Standard error response examples.
- Mobile API contract notes.
