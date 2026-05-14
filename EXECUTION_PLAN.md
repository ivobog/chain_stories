# Chain Reaction Stories Execution Plan

Date: 2026-05-13

Source documents analyzed:

- `Chain Reaction Stories Vision Document(1).docx`
- `Chain Reaction Stories Backend Srs.docx`
- `Chain Reaction Stories Backend Sdd.docx`

Repository target: `https://github.com/ivobog/chain_stories`

## 1. Executive Summary

Chain Reaction Stories is a mobile-first multiplayer AI party storytelling game. Players join a room, take turns submitting exactly one word, and the backend asks an AI model to continue the shared story using that word, the full story context, the selected writing style, and the room safety mode.

The first execution goal is not manga, video, creator mode, or a large feature set. The first goal is to prove that the text-only loop is fast, funny, safe, multiplayer, and replayable:

Create room -> join friend -> start game -> submit one word -> AI mutates story -> everyone sees it live -> repeat -> vote -> replay.

The recommended implementation path is a modular monolith backend with a mobile app client, shipped in phases. The backend owns identity, permissions, room/game state, moderation, subscriptions, story persistence, anti-repetition memory, observability, and integrations. The AI provider is a replaceable creative engine inside that controlled system.

## 2. Product Priorities

### 2.1 MVP Product Promise

The MVP is successful when two users can play a complete private game round and immediately understand why another round would be fun.

Core MVP capabilities:

- User registration and login.
- Profile creation.
- Private room creation.
- Join room by code or invite link.
- Free room limit of 2 players.
- Paid entitlement foundation for larger rooms.
- Real-time turn-based gameplay.
- One-word prompt submission.
- AI-generated story continuation.
- Whole story visible to players at all times.
- Basic moderation for submitted words and AI output.
- Word Registry v1 for anti-repetition.
- AI random word suggestion.
- End-of-game voting.
- Final story screen.
- Structured logging, metrics, health checks.
- Dockerized backend with PostgreSQL and Redis.

### 2.2 Explicitly Deferred From First MVP

- Manga panel generation.
- Video generation.
- Voice narration.
- Streamer or creator mode.
- Public rooms.
- Public leaderboards.
- Complex creator tools.
- Advanced custom decks.
- Full media worker pipeline.

### 2.3 Recommended First Writing Styles

Start with a small, high-contrast set:

- Funny
- Horror
- Batshit Crazy
- Noir Detective
- Fairy Tale
- Manga Action
- Family Friendly
- Swiss Chaos

For the earliest prototype, use only 3 to 4 styles to reduce prompt-engineering complexity. Recommended prototype set: Funny, Horror, Batshit Crazy, Family Friendly.

## 3. Core Architecture Decisions

### 3.1 Backend

Use a modular monolith:

- Java 21
- Spring Boot 3.x
- Spring Security
- JWT access tokens and refresh token rotation
- OAuth2/OIDC support for Apple and Google later in MVP
- Spring Web MVC REST APIs
- WebSocket with STOMP or native WebSocket events
- PostgreSQL as source of truth
- Flyway migrations
- Redis for active room state, pub/sub, locks, and rate limits
- Spring events for early async workflows
- Provider abstraction for OpenAI or other LLM providers
- Micrometer, OpenTelemetry, Prometheus-compatible metrics
- Structured JSON logs
- Docker and Docker Compose for local development

Reasoning: the docs strongly recommend avoiding premature microservices. A modular monolith keeps development fast while preserving boundaries for later extraction of AI orchestration, media generation, notification, moderation, and analytics services.

### 3.2 Mobile App

Recommended stack:

- React Native with Expo
- TypeScript
- API client generated or manually typed from OpenAPI contracts
- Secure token storage
- WebSocket client for room/game events
- In-app purchase integration after entitlement foundation is stable

Reasoning: Expo is the recommended stack in the vision document for fast MVP iteration across iOS and Android.

### 3.3 AI Integration

Introduce an `AiProviderClient` interface from the start. The game logic must not depend directly on a provider SDK.

The AI orchestration layer should handle:

- Prompt construction.
- Structured output requests.
- Provider timeout handling.
- Retry policy.
- Output validation.
- Safety filtering.
- Cost and latency logging.
- Model selection by environment or entitlement.

AI output must be accepted only after validation, moderation, and anti-repetition checks.

### 3.4 Real-Time State

Use PostgreSQL as durable state and Redis as active/live state.

PostgreSQL stores:

- Users and profiles.
- Subscriptions.
- Rooms and participants.
- Games and turns.
- Stories and story segments.
- Word Registry entries.
- Moderation events.
- Votes.
- Share links and generated media metadata for later phases.

Redis supports:

- Active room snapshots.
- WebSocket fanout/pub-sub.
- Turn locks.
- Duplicate submission protection.
- Rate limits.
- Short-lived connection/session helpers.

## 4. Repository Setup Plan

The repository is currently empty. Initialize it as a multi-project repo:

```text
chain_stories/
  backend/
    build.gradle or pom.xml
    src/main/java/com/chainreaction/...
    src/main/resources/
    src/test/java/com/chainreaction/...
  mobile/
    app.json
    package.json
    src/
  docs/
    architecture/
    api/
    product/
  infra/
    docker/
    local/
  .github/
    workflows/
  docker-compose.yml
  README.md
  EXECUTION_PLAN.md
```

Recommended first repository files:

- `README.md`: project overview, local setup, commands.
- `.gitignore`: Java, Node, IDE, OS, secrets.
- `docker-compose.yml`: PostgreSQL, Redis, optional local observability.
- `backend/`: Spring Boot project.
- `mobile/`: Expo project.
- `docs/`: copied or converted product/backend planning docs later if desired.
- `.env.example`: safe example configuration only.
- `.github/workflows/ci.yml`: backend test/build and mobile lint/typecheck once projects exist.

## 5. Delivery Phases

### Phase 0: Foundation and Decisions

Goal: make the empty repository executable and remove critical ambiguity.

Deliverables:

- Repository structure.
- Backend Spring Boot skeleton.
- Mobile Expo skeleton.
- Docker Compose with PostgreSQL and Redis.
- Environment configuration pattern.
- Basic CI pipeline.
- Initial architectural decision records.
- Product decision log for open questions.

Key decisions to close:

- Require all MVP players to register, or allow guests?
- First paid room limit: 6, 8, or 10 players?
- Launch safety modes: FAMILY and TEEN only, or include ADULT_SAFE?
- Free daily round limits in MVP?
- First AI provider and model family?
- Story retention policy: persistent by default or expiring?
- Include AI random word suggestion in MVP or phase 2?
- Introduce sharing from day one or after gameplay validation?

Recommendation:

- Require registered users for MVP.
- Set Plus room limit to 8.
- Launch with FAMILY and TEEN only.
- Add daily free round limits after instrumentation, not before.
- Keep AI provider configurable.
- Store stories by default with user deletion support.
- Include AI random word suggestion in MVP because it is part of the core fun.
- Defer paid sharing until after the core loop is playable.

Exit criteria:

- A developer can clone the repo, run one command, and start backend dependencies.
- Backend health endpoint works.
- Mobile app starts with a placeholder screen.
- CI validates at least backend compile/tests.

### Phase 1: Backend Foundation

Goal: build a secure, testable backend base.

Deliverables:

- Spring Boot 3.x project using Java 21.
- PostgreSQL connection.
- Flyway migrations.
- Redis connection.
- Common error envelope with `errorCode`, `message`, and `correlationId`.
- Structured JSON logging.
- Correlation ID filter.
- Health checks.
- User and profile tables.
- Email/password registration and login.
- Password hashing.
- JWT access token issuing and validation.
- Refresh token rotation or equivalent session handling.
- Current-user endpoint.
- Account status values: ACTIVE, SUSPENDED, DELETED.

Primary APIs:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `GET /api/v1/me`
- `GET /actuator/health`

Testing:

- Unit tests for token service, password validation, account status rules.
- Integration tests for registration, login, refresh, and current-user flow.

Exit criteria:

- Users can register and login.
- Protected APIs reject invalid or expired tokens.
- Suspended users can be blocked by authorization checks.
- Logs include correlation IDs and do not expose secrets.

### Phase 2: Rooms and Entitlement Foundation

Goal: allow users to create and join private rooms with correct plan limits.

Deliverables:

- Subscription and entitlement model.
- Default FREE entitlement.
- Plan limit service.
- Room entity.
- Room settings: writing style, language, safety mode, max players, turn limit, turn timeout, visibility.
- Room code generator.
- Room participant model.
- Host/player roles.
- Room create, join, close, kick player.
- Free user max player limit of 2.
- Paid plan support in data model even if receipt validation is mocked.

Primary APIs:

- `GET /api/v1/rooms`
- `POST /api/v1/rooms`
- `GET /api/v1/rooms/code/{roomCode}/preview`
- `POST /api/v1/rooms/{roomCode}/join`
- `GET /api/v1/rooms/{roomId}`
- `POST /api/v1/rooms/{roomId}/close`
- `PATCH /api/v1/rooms/{roomId}/settings`
- `POST /api/v1/rooms/{roomId}/leave`
- `POST /api/v1/rooms/{roomId}/participants/{userId}/kick`
- `GET /api/v1/me/entitlements`
- `GET /api/v1/me/subscription`
- `POST /api/v1/me/subscription/mock-purchase`
- `POST /api/v1/me/subscription/cancel`

Testing:

- Room creation with FREE plan.
- Rejection when FREE user requests more than 2 players.
- Paid entitlement allows larger rooms up to plan limit.
- Join rejects full, closed, expired, or banned rooms.
- Host-only actions enforce room permissions.

Exit criteria:

- User A can create a private room.
- User B can join by code.
- The backend persists room and participant state.
- Room limits are enforced through entitlements.

### Phase 3: Game Engine and Story State

Goal: start games, maintain turn order, and persist full story state.

Deliverables:

- Game entity and state machine.
- GameTurn entity.
- Story entity.
- StorySegment entity.
- Opening story segment generation placeholder.
- Turn order calculation.
- Start game endpoint.
- Get game state endpoint.
- Turn timeout data model.
- Game completion after configured turn limit.
- Final story reconstruction.

Primary APIs:

- `POST /api/v1/rooms/{roomId}/games/start`
- `GET /api/v1/games/{gameId}`
- `POST /api/v1/games/{gameId}/turns/{turnId}/submit-word`
- `POST /api/v1/games/{gameId}/turns/{turnId}/skip-expired`

Implemented slices:

- Durable `games`, `game_turns`, `stories`, and `story_segments` tables.
- Host-only game start from room lobby.
- Minimum two active players required.
- First turn assigned from room join order.
- Opening story segment placeholder persisted.
- Game state retrieval for active room participants.
- New joins rejected after a room becomes active.
- Deterministic mock submit-word endpoint.
- One-word validation and current-player enforcement.
- Turn advancement by room join order.
- Game moves to `VOTING` after the configured turn limit.
- Expired current turns can be marked `SKIPPED` and advanced by any active room participant.
- Game responses include backend-reconstructed `fullStory` text from ordered story segments.
- Game responses include ordered turn history with submitted, skipped, and active turn statuses.
- Game responses expose lifecycle timestamps for game start and voting transition completion.
- Turn responses expose `submittedAt` when a turn is submitted or skipped.

Testing:

- Only host can start game.
- Game cannot start without minimum player count.
- No fixed story theme is required.
- First turn is assigned correctly.
- Full story can be reconstructed from ordered segments.
- Current-player and one-word submit validation are enforced.
- Non-expired turns cannot be skipped; expired turns advance turn order.
- `fullStory` is reconstructed from persisted segments after start, submit, and voting transition.
- Ordered `turns` reflect active, submitted, and skipped states across reconnects.
- Active games expose `startedAt`; games moved to `VOTING` expose `completedAt`.
- Active turns have no `submittedAt`; submitted and skipped turns do.
- Game moves to voting after configured turn limit.

Exit criteria:

- A room can start a game.
- A story exists for the game.
- Current turn is visible.
- Full story state is retrievable after reconnect.

### Phase 4: WebSocket Multiplayer

Goal: make the room and game feel live.

Deliverables:

- Authenticated WebSocket connection.
- Room topic subscription.
- User-specific event queue.
- Common WebSocket event envelope.
- Event publishing from room and game flows.
- Reconnect support through `GET /games/{gameId}`.

Event types:

- `PLAYER_JOINED`
- `PLAYER_LEFT`
- `PLAYER_KICKED`
- `ROOM_CLOSED`
- `GAME_STARTED`
- `TURN_STARTED`
- `WORD_SUBMITTED`
- `AI_GENERATION_STARTED`
- `STORY_SEGMENT_ADDED`
- `TURN_SKIPPED`
- `VOTING_STARTED`
- `VOTE_SUBMITTED`
- `VOTING_RESULTS_READY`
- `GAME_FINISHED`
- `ERROR_EVENT`

Testing:

- WebSocket handshake rejects unauthenticated clients.
- Joining a room emits `PLAYER_JOINED`.
- Starting a game emits `GAME_STARTED` and `TURN_STARTED`.
- Reconnected clients can retrieve full current state.

Implemented first slice:

- STOMP WebSocket endpoint at `/ws/game`.
- Simple broker topics under `/topic`.
- JWT authentication for STOMP `CONNECT` frames through the existing access token service.
- Authenticated STOMP principals are retained on the WebSocket session for later subscription authorization.
- Room topic subscriptions require active room participation.
- User queue subscriptions at `/user/queue/events` require an authenticated WebSocket user.
- Room topic envelope with `type`, `roomId`, optional `gameId`, `payload`, and `occurredAt`.
- Event publisher for `/topic/rooms/{roomId}`.
- Event publisher for `/user/queue/events`.
- Realtime publisher defers transactional sends until after successful commit.
- Room REST mutations publish `PLAYER_JOINED`, `PLAYER_LEFT`, `PLAYER_KICKED`, and `ROOM_CLOSED`.
- Kicked users receive a private `PLAYER_KICKED` event on `/user/queue/events`.
- Game REST mutations publish `GAME_STARTED`, `TURN_STARTED`, `WORD_SUBMITTED`, `STORY_SEGMENT_ADDED`, `TURN_SKIPPED`, `VOTING_STARTED`, `VOTE_RESULTS_UPDATED`, and `GAME_FINISHED`.
- Reconnected clients recover complete game, turn, and story state through `GET /api/v1/games/{gameId}`.
- Mobile now includes typed REST and STOMP WebSocket client modules for auth, room resume, room preview, room lifecycle, game, room-topic, and user-queue flows.

Testing implemented:

- Realtime publisher routes room and game events to the expected room topic.
- STOMP `CONNECT` rejects missing bearer tokens.
- STOMP `CONNECT` authenticates active users with JWT access tokens.
- Room topic subscription accepts active participants and rejects outsiders.
- User queue subscription accepts authenticated users and rejects anonymous subscriptions.
- Events queued inside transactions publish only after commit.
- Non-connect STOMP frames pass through the interceptor.
- WebSocket integration tests verify a subscribed host receives `PLAYER_JOINED`.
- WebSocket integration tests verify two subscribed participants both receive `GAME_STARTED` and `TURN_STARTED`.
- WebSocket integration tests verify kicked participants receive private user-queue events.
- Game integration tests verify a participant can miss turn events and retrieve the full current story state after reconnect.
- Mobile TypeScript verifies the REST and realtime client modules.

Exit criteria:

- Two connected clients receive room and game events. (Implemented for join and game-start events.)
- Reconnect does not lose story state. (Implemented through `GET /api/v1/games/{gameId}`.)

### Phase 5: AI Story Loop

Goal: implement the core magic: one safe word becomes one fresh story segment.

Deliverables:

- Submit word endpoint.
- One-word validation.
- Input normalization.
- Input moderation v1.
- Prompt builder.
- AI provider abstraction.
- AI provider implementation for the selected provider.
- Structured AI output schema.
- AI response validator.
- AI retry policy.
- Output moderation v1.
- Story segment persistence.
- Turn completion and next-turn creation.
- `AI_GENERATION_STARTED` and `STORY_SEGMENT_ADDED` events.
- AI latency, token usage, model, and failure logging.

Primary API:

- `POST /api/v1/games/{gameId}/turns/{turnId}/submit-word`

Structured AI output should include:

- `sentence`
- `usedWord`
- `tone`
- `intensity`
- `safetyLevel`
- `summary`
- `storyDirection`
- `tags`

Retry when:

- Output is not valid JSON.
- Required fields are missing.
- Submitted word is ignored.
- Output is too long.
- Output violates safety mode.
- Output rewrites the full story instead of adding a segment.
- Output is too similar to previous usage.

Testing:

- Non-current players cannot submit.
- Empty input is rejected.
- Multi-word input is rejected for MVP.
- Unsafe words are rejected.
- Safe input triggers AI generation.
- AI output is validated and stored.
- Accepted segment is broadcast to all players.
- AI failure does not corrupt turn state.

Exit criteria:

- Two players can take turns submitting words.
- The story grows live after each accepted word.
- Backend remains authoritative over turn state.

Implemented first slice:

- Story generation service orchestrates word moderation, prompt building, provider execution, output validation, and telemetry logging.
- Provider-neutral `StoryAiProvider` abstraction.
- `AI_PROVIDER` selects the active provider by environment; `mock` is the current default.
- Deterministic mock provider returning the structured Phase 5 output shape with configurable `AI_MOCK_MODEL`.
- OpenAI provider implementation behind `AI_PROVIDER=openai`, configured by `OPENAI_API_KEY`, `AI_OPENAI_MODEL`, and `AI_OPENAI_BASE_URL`.
- OpenAI provider calls use configurable connect and read timeouts through `AI_OPENAI_CONNECT_TIMEOUT` and `AI_OPENAI_READ_TIMEOUT`.
- OpenAI requests use the Responses API with a strict structured JSON schema matching the internal story generation output fields.
- Structured AI JSON parser maps provider JSON payloads into the internal story generation output schema.
- Prompt builder includes writing style, language, safety mode, required word, and current story context.
- Input moderation normalizes one-word submissions and blocks an initial unsafe word list.
- Output validator enforces required fields, submitted-word usage, sentence length, intensity range, and family-mode safety.
- Output moderation v1 rejects blocked generated terms and stricter family-mode unsafe generated content before persistence.
- AI generation retries provider errors or invalid generated output up to `AI_GENERATION_MAX_ATTEMPTS` attempts.
- AI generation attempts are persisted with game id, turn id, normalized word, attempt number, status, provider, model, token counts, latency, and failure reason.
- Attempt persistence uses an independent transaction so provider failures remain visible even when the turn submission rolls back.
- AI generation attempts and exhausted retry failures are exported through Micrometer metrics with provider/status tags.
- Exhausted AI generation failures return a sanitized `AI_GENERATION_FAILED` API error while raw provider/validation reasons stay in telemetry.
- Output validation rejects responses that rewrite the full story instead of adding a segment.
- Submit-word flow stores the validated generated sentence and leaves turn/story state untouched when moderation or generation validation fails.
- `AI_GENERATION_STARTED` is now part of the realtime event contract and mobile event type union.

Testing implemented:

- Word moderation unit coverage for normalization, multi-word rejection, and unsafe-word rejection.
- Output moderation unit coverage for safe output, blocked generated terms, and family-mode unsafe generated content.
- Structured JSON parser unit coverage for provider output mapping, ignored unknown fields, token/model metadata, and invalid JSON rejection.
- Mock provider unit coverage for provider name, configured model, generated sentence, and token estimates.
- OpenAI provider unit coverage for request shape, bearer authentication, structured output mapping, token metadata, missing-key rejection, and malformed response handling.
- Provider selection coverage confirms default `mock` wiring and `AI_PROVIDER=openai` Spring wiring.
- Story generation output validator unit coverage for accepted output, ignored-word rejection, family-mode safety rejection, and full-story rewrite rejection.
- Story generation service unit coverage for retry success, sanitized retry exhaustion, non-retryable input moderation failure, and retryable output moderation failure.
- Story generation service unit coverage confirms AI attempt counters and exhausted retry failure counters are emitted without retrying input moderation failures.
- Game integration coverage confirms accepted submissions persist AI attempt telemetry.
- Game integration coverage confirms rejected unsafe input does not submit the current turn or add a story segment.

### Phase 6: Word Registry and Anti-Repetition v1

Goal: reduce repeated jokes and repeated story twists.

Deliverables:

- Word Registry table.
- Previous usage lookup by normalized word, style, language, and recent time window.
- Previous usage prompt context.
- Simple text similarity service.
- Configurable similarity thresholds.
- Regeneration when output is too similar.
- Registry persistence after accepted segment.
- Pruning or archive strategy documented.

Initial thresholds:

- Same word, same style, same room: 0.78
- Same word, same style, global recent history: 0.86
- Same word, different style: 0.92

Testing:

- Accepted word usage is stored.
- Previous usage context is supplied to prompt builder.
- Similar output can be rejected.
- Accepted regenerated output is stored once.

Exit criteria:

- Reusing the same word and style has access to prior usage memory.
- The backend can reject and retry obviously repetitive outputs.

Implemented first slice:

- Durable `word_registry_entries` table for accepted word usage memory.
- Word registry entity, repository, and service.
- Accepted submit-word flow records normalized word, game, room, turn, segment, player, style, language, generated sentence, and timestamp after the story segment is persisted.
- Failed validation/moderation submissions do not create registry entries.
- Story generation fetches recent accepted usages for the submitted word, writing style, and language before prompt construction.
- Previous usage lookup is bounded by configurable `WORD_REGISTRY_RECENT_WINDOW_DAYS`, defaulting to 30 days.
- Prompt builder includes prior usage context so providers can avoid repeating old jokes, images, or twists.
- Token-based similarity checks reject and retry generated output that is too close to recent prior usage in the same room.
- Similarity threshold is configurable with `WORD_REGISTRY_SIMILARITY_THRESHOLD` and defaults to `0.78`.
- Similarity rejections are exported through `word_similarity_rejections_total` with provider, writing style, and language tags.
- Retention strategy is documented in `docs/operations/word-registry-retention.md`: active prompt memory is cutoff-bounded, inactive rows are retained for beta debugging/tuning, and a later scheduled pruning job should delete or anonymized-archive old rows before public launch.

Testing implemented:

- Game integration coverage confirms accepted word submissions create one registry entry linked to the turn/player/style/language/sentence.
- Game integration coverage confirms rejected submissions leave the word registry empty.
- Prompt builder unit coverage confirms previous usage context is included and empty memory is labeled.
- Story generation service unit coverage confirms previous usage memory reaches the provider request and prompt.
- Story generation service unit coverage confirms similar generated output is rejected and retried.
- Story generation service unit coverage confirms similarity rejections increment the word similarity metric.
- Story similarity unit coverage confirms similar output is blocked and distinct output is accepted.
- Word registry service unit coverage confirms recent registry entries map into prompt memory.

### Phase 7: Random Word Suggestion

Goal: help stuck players produce safe, funny, style-aware words.

Deliverables:

- Random word endpoint.
- Prompt builder for word suggestions.
- Suggestion moderation.
- Rate limiting.
- Suggestion analytics storage.
- Client-visible response with one word and safety level.

Primary API:

- `POST /api/v1/games/{gameId}/random-word`

Testing:

- Suggestion considers style, language, safety mode, previous words, and current story context.
- Unsafe suggestions are not returned.
- Suggestions are editable by the client; backend still validates final submitted word.
- Endpoint is rate-limited.

Exit criteria:

- Current player can request a safe word suggestion during their turn.

Implemented first slice:

- Added `POST /api/v1/games/{gameId}/random-word`.
- Current active turn player can request one suggested word; non-current participants are rejected.
- Suggestions use curated style-aware candidate pools, room language/safety context, current story text, and previously accepted words for the game.
- Added a word suggestion prompt builder that captures style, language, safety mode, previous words, and current story context for the future AI suggestion provider contract.
- Suggested words are passed through the same one-word moderation path used by submitted words.
- Unsafe candidate suggestions are skipped before a response is returned.
- Response returns the client-visible word, normalized word, safety level, writing style, and language.
- Successful suggestions are recorded in `word_suggestion_events` with game, room, turn, player, word, style, language, safety level, story length, previous-word count, and timestamp.
- Random-word requests are rate-limited per player/game with configurable `AI_SUGGESTION_RATE_LIMIT_PER_WINDOW` and `AI_SUGGESTION_RATE_LIMIT_WINDOW_SECONDS`.

Testing implemented:

- Word suggestion service unit coverage confirms style-aware safe suggestions, previous-word exclusion, and unsafe candidate skipping.
- Word suggestion prompt builder unit coverage confirms suggestion context and empty-memory labeling.
- Word suggestion rate limiter unit coverage confirms request limits and independent player/game buckets.
- Game integration coverage confirms the current player can request a random word and non-current players cannot.
- Game integration coverage confirms successful suggestions create one analytics row and rejected non-current requests do not create extra rows.
- Game integration coverage confirms rate-limited requests return `RATE_LIMITED` and do not create analytics rows.

### Phase 8: Voting and Results

Goal: complete the gameplay loop.

Deliverables:

- Vote entity and vote categories.
- Voting state transition after turn limit.
- Vote submission endpoint.
- Duplicate vote prevention.
- Result calculation.
- Result persistence.
- Result broadcast.

Vote categories:

- `FUNNIEST_WORD`
- `BEST_SABOTAGE`
- `WEIRDEST_TWIST`
- `BEST_AI_SENTENCE`
- `MVP_PLAYER`

Primary APIs:

- `POST /api/v1/games/{gameId}/votes`
- `GET /api/v1/games/{gameId}/votes/results`

Testing:

- Voting starts at game end.
- Players can vote once per category.
- Duplicate votes are rejected.
- Results are calculated and broadcast.

Exit criteria:

- A game can finish with persisted and visible voting results.

Implemented first slice:

- Added `votes` table with one vote per game/player/category.
- Added vote categories: `FUNNIEST_WORD`, `BEST_SABOTAGE`, `WEIRDEST_TWIST`, `BEST_AI_SENTENCE`, and `MVP_PLAYER`.
- Added `POST /api/v1/games/{gameId}/votes`.
- Added `GET /api/v1/games/{gameId}/votes/results`.
- Voting is accepted only while the game status is `VOTING`.
- Active room participants can vote once per category.
- Player-target categories require a player target; story categories require a playable story segment target.
- Duplicate votes return `DUPLICATE_VOTE`.
- Vote results aggregate ranked targets per category with vote counts.
- Vote result projections are persisted after each accepted vote and broadcast to room subscribers.
- Games move from `VOTING` to `FINISHED` after every active participant has voted in every category.
- Voting results remain visible after a game is `FINISHED`.

Testing implemented:

- Game integration coverage confirms voting is rejected before `VOTING`.
- Game integration coverage confirms story-segment and player-target votes are persisted.
- Game integration coverage confirms duplicate category votes are rejected.
- Game integration coverage confirms vote results return category aggregates and counts.
- Game integration coverage confirms calculated vote results are persisted for later reads.
- Game integration coverage confirms completed voting finishes the game and keeps results visible.

### Phase 9: Mobile MVP

Goal: provide the playable mobile experience for the backend MVP.

Deliverables:

- Expo app setup.
- Authentication screens.
- Secure token storage.
- Home screen.
- Create room screen.
- Join room screen.
- Lobby screen.
- Game screen with whole story timeline.
- One-word input.
- AI random word button.
- Turn state display.
- AI generation status.
- Voting screen.
- Final story screen.
- Basic profile/settings screen.
- API client and WebSocket client.
- Error state handling.

Key UX rules:

- Players always see the whole story so far.
- Current turn ownership must be unmistakable.
- AI generation should feel active and short.
- Random word suggestion is a helper, not a forced card.
- Do not expose raw backend or AI provider errors.

Testing:

- Manual two-device happy path.
- Component tests for core screens where practical.
- API contract tests or typed API fixtures.
- Reconnect flow test.

Exit criteria:

- Two users can complete a full round from mobile devices or simulators.

Implemented first slice:

- Replaced the placeholder Expo screen with a real mobile app shell.
- Added login and registration screens against the backend auth API.
- Added secure persisted session storage with `expo-secure-store`.
- Added mobile refresh-token rotation on restored sessions, with expired-session cleanup and transient-refresh fallback.
- Added protected mobile API action retry after access-token expiry by refreshing once with the stored refresh token.
- Added best-effort mobile logout revocation for refresh tokens before clearing local session state.
- Added backend URL persistence for local device/simulator testing.
- Added automatic room loading after sign-in/session restore plus an empty-room home state.
- Added a home screen with room refresh, create room, join room, and open room actions.
- Replaced instant default room creation with a mobile create-room settings screen.
- Added mobile room controls for writing style, safety mode, visibility, language, max players, turn limit, and turn timeout.
- Added a dedicated join-room screen that previews host, status, capacity, style, safety, and joinability before joining by code.
- Added a lobby view with participant list, refresh, leave room, sign out, and host start-game action.
- Added mobile lobby room-code sharing through the native share sheet.
- Added mobile host lobby controls for closing rooms and kicking joined players before the game starts.
- Added mobile host lobby settings editing for writing style, safety, visibility, language, max players, turn limit, and turn timeout.
- Added `GET /api/v1/rooms/{roomId}/game` for mobile active-room game resume.
- Added mobile active-room game opening from the lobby for reconnect/resume flows.
- Added foreground resume refresh for Home, Lobby, and Game screens after mobile app backgrounding.
- Added a playable game screen after host start.
- Added whole-story timeline rendering for mobile gameplay.
- Added current-turn ownership display.
- Added display-name resolution for current-turn and voting target labels when room participants are available.
- Added active-turn countdown display from the backend turn expiry timestamp.
- Added one-word submit controls for the current player.
- Added client-side one-word validation and submit disabling before calling the backend turn endpoint.
- Added in-game AI generation indicator driven by submit actions and `AI_GENERATION_STARTED` realtime events.
- Added mobile random word suggestion request and tap-to-use flow.
- Added skip-expired-turn and game refresh controls.
- Added mobile voting and final-results panel for `VOTING` and `FINISHED` games.
- Added mobile final-story sharing through the native share sheet for finished games.
- Added vote target selection for player-target and story-segment categories.
- Added vote result refresh and top result summaries per category.
- Added queued mobile STOMP subscriptions so room/user subscriptions attach after connection.
- Added mobile live room, game, AI generation, vote result, and game-finished event handling.
- Added mobile room lifecycle event handling for remote game start, room close, and private kick cleanup.
- Added private kicked-event handling that clears the active room/game state.
- Added typed mobile profile APIs for `GET /me` and `PATCH /me/profile`.
- Added a basic mobile settings/profile screen with display name, avatar URL, and favorite style editing.
- Extended the typed mobile API client for skip-turn, random word, vote submission, and vote results.
- Extended realtime/mobile API types for `VOTE_RESULTS_UPDATED` and `GAME_FINISHED`.
- Added Vitest-based mobile API client contract tests for registration and login request shapes.
- Added Vitest-based mobile API client contract tests for preview, settings update, vote submission, and parsed API errors.
- Added Vitest-based mobile API client contract tests for room creation, join, lobby lifecycle controls, and profile settings.
- Added Vitest-based mobile API client contract tests for room-game resume, submit-word, random-word, skip-expired-turn, and vote-results calls.
- Added Vitest-based mobile realtime client tests for authenticated connect, queued subscriptions, and room/user event routing.
- Added a Phase 9 manual mobile QA checklist for two-client happy path, reconnect/resume, realtime lifecycle, and session handling.

Testing implemented:

- Mobile TypeScript coverage confirms the Phase 9 shell and API contracts compile.
- Mobile TypeScript coverage confirms create-room settings compile.
- Backend integration coverage confirms active participants can fetch a game by room and outsiders cannot.
- Mobile TypeScript coverage confirms the game screen and current-turn actions compile.
- Mobile TypeScript coverage confirms current-turn display-name resolution compiles.
- Mobile TypeScript coverage confirms active-turn countdown display compiles.
- Mobile TypeScript coverage confirms one-word input validation wiring compiles.
- Mobile TypeScript coverage confirms in-game AI generation indicator wiring compiles.
- Mobile TypeScript coverage confirms voting/result screen wiring compiles.
- Mobile TypeScript coverage confirms final-story sharing compiles.
- Mobile TypeScript coverage confirms realtime event wiring compiles.
- Mobile realtime client tests verify queued STOMP subscriptions flush after `CONNECTED`.
- Mobile realtime client tests verify room-topic and user-queue messages route to the right handlers.
- Mobile TypeScript coverage confirms profile/settings wiring compiles.
- Mobile TypeScript coverage confirms the join-room preview flow compiles.
- Mobile TypeScript coverage confirms lobby leave, close, and kick controls compile.
- Mobile TypeScript coverage confirms lobby room-code sharing compiles.
- Mobile TypeScript coverage confirms room lifecycle event cleanup compiles.
- Mobile TypeScript coverage confirms host lobby settings editing compiles.
- Mobile TypeScript coverage confirms foreground resume refresh wiring compiles.
- Mobile TypeScript coverage confirms restored-session refresh handling compiles.
- Mobile TypeScript coverage confirms protected API refresh-and-retry action handling compiles.
- Mobile TypeScript coverage confirms refresh-token logout revocation wiring compiles.
- Mobile API client contract tests verify unauthenticated registration and login request shapes.
- Mobile API client contract tests verify key REST paths, methods, headers, JSON bodies, and parsed error responses.
- Mobile API client contract tests verify room creation, join, lobby leave, close, kick, start, and profile request shapes.
- Mobile API client contract tests verify gameplay resume, turn action, suggestion, skip, and results request shapes.
- Mobile API client contract tests verify unauthenticated refresh-token rotation request shape.
- Mobile API client contract tests verify unauthenticated logout revocation request shape.
- Mobile API client contract tests verify the signed-in room list request used by Home bootstrap.
- Manual acceptance checklist is documented at `docs/testing/mobile-phase-9-manual-checklist.md`.

### Phase 10: Hardening and MVP Release Readiness

Goal: make the system safe enough and observable enough for private beta.

Deliverables:

- Rate limits for auth, random word, submit word, and AI generation.
- Moderation event audit trail.
- Admin moderation review foundation.
- Host kick/close room controls.
- Metrics dashboards or documented Prometheus queries.
- OpenTelemetry traces for key flows.
- Health/readiness endpoints.
- Docker image build.
- Deployment documentation.
- Staging environment.
- Basic load test for room/game flow.
- Security checklist.
- Privacy/account deletion checklist.

Metrics:

- `rooms_created_total`
- `games_started_total`
- `games_finished_total`
- `ai_generation_duration_ms`
- `ai_generation_failures_total`
- `moderation_blocks_total`
- `word_similarity_rejections_total`
- `websocket_connections_active`
- `random_word_requests_total`
- `subscription_upgrades_total`

Exit criteria:

- Backend services run in containers.
- Game state survives mobile reconnect.
- Logs and metrics are useful for debugging.
- AI provider failures are handled without corrupting game state.
- Private beta can begin.

## 6. Data Model Implementation Order

Implement tables in this order to reduce migration churn:

1. `users`
2. `user_profiles`
3. `subscriptions`
4. `rooms`
5. `room_participants`
6. `games`
7. `game_turns`
8. `stories`
9. `story_segments`
10. `moderation_events`
11. `word_registry_entries`
12. `votes`
13. `share_links`
14. `generated_media`

`share_links` and `generated_media` can be delayed until paid sharing/media phases unless it is cheaper to add metadata tables early.

## 7. API Contract Summary

Base path: `/api/v1`

Authentication:

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`

Me/profile:

- `GET /me`
- `GET /me/entitlements`
- `GET /me/subscription`
- `POST /me/subscription/mock-purchase`
- `POST /me/subscription/cancel`
- `PATCH /me/profile`

Rooms:

- `GET /rooms`
- `POST /rooms`
- `GET /rooms/code/{roomCode}/preview`
- `POST /rooms/{roomCode}/join`
- `GET /rooms/{roomId}`
- `POST /rooms/{roomId}/close`
- `PATCH /rooms/{roomId}/settings`
- `POST /rooms/{roomId}/leave`
- `POST /rooms/{roomId}/participants/{userId}/kick`

Games:

- `POST /rooms/{roomId}/games/start`
- `GET /games/{gameId}`
- `POST /games/{gameId}/turns/{turnId}/submit-word`
- `POST /games/{gameId}/random-word`

Voting:

- `POST /games/{gameId}/votes`
- `GET /games/{gameId}/votes/results`

Sharing, post-core:

- `POST /stories/{storyId}/share-links`
- `DELETE /stories/{storyId}/share-links/{shareId}`

WebSocket:

- Connection path: `/ws/game`
- Room topic: `/topic/rooms/{roomId}`
- User queue: `/user/queue/events`

## 8. Testing Strategy

### 8.1 Unit Tests

Cover:

- Room limit rules.
- Entitlement decisions.
- Turn validation.
- One-word validation.
- Moderation decisions.
- Prompt builder.
- AI output validation.
- Anti-repetition logic.
- Voting rules.
- Error mapping.

### 8.2 Integration Tests

Cover:

- Registration and login.
- Room creation with PostgreSQL.
- Join room flow.
- Start game flow.
- Submit word flow with mocked AI provider.
- WebSocket event publishing.
- Redis-backed active room state.
- Subscription entitlement checks.

### 8.3 Contract Tests

Cover:

- Mobile REST request/response schemas.
- WebSocket event payloads.
- AI provider structured output mapping.
- Standard error envelope.

### 8.4 End-to-End Tests

Core path:

1. Register two users.
2. Create room.
3. Join room.
4. Start game.
5. Submit word.
6. Mock or call AI segment generation.
7. Verify both players receive story update.
8. Complete turn limit.
9. Vote.
10. Verify results.

## 9. Security and Safety Checklist

Backend:

- HTTPS only in production.
- Strong password hashing.
- JWT validation on protected REST APIs.
- WebSocket authentication.
- Room-level authorization.
- Host-only authorization.
- Turn-owner authorization.
- Subscription entitlement authorization.
- Input validation on all endpoints.
- Rate limiting on expensive and abuse-prone endpoints.
- Secrets outside source control.
- No tokens, passwords, provider keys, or payment secrets in logs.

AI:

- Treat submitted words as untrusted input.
- Delimit user input in prompts.
- Do not allow submitted words to override system instructions.
- Require structured AI responses.
- Validate required fields.
- Moderate AI output before broadcast.
- Do not expose raw provider errors to mobile clients.
- Avoid logging full prompts unless explicitly configured and protected.

Moderation:

- Support FAMILY and TEEN in MVP.
- Record moderation events.
- Reject unsafe words before AI generation.
- Reject unsafe AI output before broadcast.
- Add report flow and admin review foundation.

Privacy:

- Support account deletion.
- Support story deletion or anonymization where legally required.
- Distinguish private stories from shared stories.
- Store only necessary personal data.

## 10. Release Milestones

### Milestone A: Technical Skeleton

User value: none yet, but development can move quickly.

Includes:

- Repo structure.
- Backend skeleton.
- Mobile skeleton.
- Docker Compose.
- CI.
- Health checks.

### Milestone B: Rooms Without AI

User value: users can authenticate, create rooms, and join.

Includes:

- Auth.
- Profiles.
- Rooms.
- Entitlements foundation.
- Lobby state.

### Milestone C: Playable Mock-AI Game

User value: the full game loop works with deterministic or mocked AI text.

Includes:

- Game start.
- Turn order.
- Submit word.
- Story segment persistence.
- WebSocket events.
- Voting.

### Milestone D: Real AI Private Alpha

User value: the actual product magic is testable.

Includes:

- AI provider integration.
- Prompt builder.
- Moderation.
- Random word suggestion.
- Anti-repetition v1.
- Logs and metrics for AI behavior.

### Milestone E: Mobile Private Beta

User value: two real users can play end to end on mobile.

Includes:

- Expo mobile app.
- Auth screens.
- Create/join/lobby/game/voting/final screens.
- Reconnect.
- Basic release hardening.

### Milestone F: Monetization and Sharing Beta

User value: paid feature assumptions can be tested.

Includes:

- Receipt validation.
- Plus/Creator entitlements.
- Paid room sizes.
- Share links.
- Story cards if the text game is already fun.

## 11. Risk Register

### AI Cost Risk

Risk: every turn calls AI, making active usage expensive.

Mitigations:

- Keep generated text short.
- Add free daily round limits after measuring usage.
- Use smaller models for free users where quality permits.
- Track token usage per generation.
- Rate-limit random word and submit-word endpoints.

### Latency Risk

Risk: players lose interest if AI generation feels slow.

Mitigations:

- Use short outputs.
- Broadcast `AI_GENERATION_STARTED`.
- Add timeout and retry policy.
- Use fast model fallback.
- Consider streaming later if needed.

### Safety Risk

Risk: players submit unsafe words or AI generates unsafe content.

Mitigations:

- Moderate submitted words before AI generation.
- Moderate AI output before broadcast.
- Use safety modes.
- Add blocklists.
- Store moderation events.
- Support reporting, host kick, and room close.

### Repetition Risk

Risk: the game becomes boring if the same word produces the same joke.

Mitigations:

- Word Registry v1.
- Previous usage context in prompts.
- Similarity checks.
- Configurable creativity by style.
- Later embedding-based similarity.

### Scope Risk

Risk: visual media and creator features consume time before the core game is proven.

Mitigations:

- Defer manga, video, voice, and creator mode.
- Ship story cards only after text gameplay retention is promising.
- Keep first beta focused on two-player and small-group text gameplay.

## 12. Open Questions and Recommended Answers

| Question | Recommended answer |
| --- | --- |
| Should guest users be allowed in MVP? | No. Require registration for simpler abuse control, persistence, and beta feedback. |
| First paid player limit: 6, 8, or 10? | 8. It fits friend groups without pushing real-time complexity too early. |
| Include ADULT_SAFE at launch? | No. Start with FAMILY and TEEN until moderation is stronger. |
| Add free daily round limits? | Not at first private alpha. Add after measuring AI cost. |
| Which AI provider first? | Use a provider abstraction and configure the first provider by environment. |
| Story retention policy? | Store by default, allow deletion, revisit auto-expiry after privacy review. |
| Random word in MVP or phase 2? | Include in MVP; it is simple and reinforces the product loop. |
| Sharing paid-only from beginning? | Build entitlement hooks early, but ship sharing after gameplay validation. |

## 13. Immediate Next Actions

1. Create the initial repository structure.
2. Add `README.md`, `.gitignore`, `.env.example`, and `docker-compose.yml`.
3. Scaffold the Spring Boot backend.
4. Add PostgreSQL, Redis, Flyway, health checks, and structured error handling.
5. Scaffold the Expo mobile app.
6. Add CI for backend compile/tests and later mobile lint/typecheck.
7. Start Phase 1 implementation with auth, users, and profiles.

## 14. MVP Acceptance Criteria

The backend and mobile MVP are acceptable when:

- A user can register, log in, and create a profile.
- A user can create a private room.
- A second user can join the room.
- The host can start a game.
- The game starts without theme selection.
- Players can see the whole story so far.
- The current player can submit one word.
- Unsafe input is rejected.
- Safe input triggers AI story generation.
- The generated segment is moderated, stored, and broadcast to all players.
- The next turn starts automatically.
- The story completes after configured turns.
- Players can vote at the end.
- Vote results are stored and displayed.
- Free users cannot create rooms with more than 2 players.
- Backend logs and metrics are available for debugging.
- Backend services run in containers.
- Game state survives mobile reconnect.

## 15. North Star

The first mission is to answer one question:

Do people laugh and immediately ask for another round?

Everything in the first implementation should serve that test. If the text-only multiplayer loop is fun, story cards, manga panels, video, and creator mode become growth accelerators. If the loop is not fun, media generation will only decorate a weak core.
