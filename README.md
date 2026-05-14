# Chain Stories

Chain Reaction Stories is a mobile-first multiplayer AI party storytelling game. Players join a room, submit one word at a time, and the backend uses AI to mutate the shared story in real time.

## Repository Layout

```text
.
├── backend/              # Spring Boot modular monolith
├── mobile/               # Expo React Native app
├── docs/                 # Architecture and product planning
├── infra/                # Local/deployment infrastructure notes
├── docker-compose.yml    # Local PostgreSQL and Redis
└── EXECUTION_PLAN.md     # Phase plan derived from the source documents
```

## Local Prerequisites

- Java 21
- Maven 3.9+
- Node.js 22+
- npm 10+
- Docker Desktop with Docker Compose

## Start Local Dependencies

```powershell
docker compose up -d
```

This starts:

- PostgreSQL on `localhost:15432`
- Redis on `localhost:6379`

Run the backend in Docker as well:

```powershell
docker compose --profile app up -d --build backend
```

## Backend

```powershell
cd backend
mvn spring-boot:run
```

Useful endpoints:

- `GET http://localhost:8080/actuator/health`
- `GET http://localhost:8080/api/v1/status`
- `POST http://localhost:8080/api/v1/auth/register`
- `POST http://localhost:8080/api/v1/auth/login`
- `POST http://localhost:8080/api/v1/auth/refresh`
- `POST http://localhost:8080/api/v1/auth/logout`
- `POST http://localhost:8080/api/v1/auth/password-reset/request`
- `POST http://localhost:8080/api/v1/auth/password-reset/confirm`
- `GET http://localhost:8080/api/v1/me`
- `PATCH http://localhost:8080/api/v1/me/profile`
- `DELETE http://localhost:8080/api/v1/me`
- `GET http://localhost:8080/api/v1/me/entitlements`
- `GET http://localhost:8080/api/v1/me/subscription`
- `POST http://localhost:8080/api/v1/me/subscription/mock-purchase`
- `POST http://localhost:8080/api/v1/me/subscription/cancel`
- `GET http://localhost:8080/api/v1/rooms`
- `POST http://localhost:8080/api/v1/rooms`
- `GET http://localhost:8080/api/v1/rooms/code/{roomCode}/preview`
- `POST http://localhost:8080/api/v1/rooms/{roomCode}/join`
- `GET http://localhost:8080/api/v1/rooms/{roomId}`
- `POST http://localhost:8080/api/v1/rooms/{roomId}/close`
- `PATCH http://localhost:8080/api/v1/rooms/{roomId}/settings`
- `POST http://localhost:8080/api/v1/rooms/{roomId}/leave`
- `POST http://localhost:8080/api/v1/rooms/{roomId}/participants/{userId}/kick`
- `POST http://localhost:8080/api/v1/rooms/{roomId}/games/start`
- `GET http://localhost:8080/api/v1/rooms/{roomId}/game`
- `GET http://localhost:8080/api/v1/games/{gameId}`
- `POST http://localhost:8080/api/v1/games/{gameId}/turns/{turnId}/submit-word`
- `POST http://localhost:8080/api/v1/games/{gameId}/turns/{turnId}/skip-expired`
- `WS/STOMP http://localhost:8080/ws/game`

## Mobile

```powershell
cd mobile
npm install
npm run start
```

The mobile app now has the Phase 9 starter shell: persisted login/register with restored-session refresh, protected-request retry, and logout revocation, automatic room loading on sign-in/session restore, configurable room creation, room-code preview before join, lobby room-code sharing, lobby participant and host controls, host lobby settings editing, active-room game resume, foreground resume refresh, an initial game screen with whole-story timeline, display-name turn ownership, active-turn countdown, client-side one-word validation, in-game AI generation status, random word suggestions, skip-expired-turn actions, voting/final-results panels, final-story sharing, live room/game/vote/lifecycle updates over STOMP, and a basic profile/settings screen. `mobile/src/api` has typed REST and WebSocket clients with focused auth/session, room, lobby, profile, gameplay, vote, and realtime contract tests ready for broader manual device testing.

Mobile contract checks:

```powershell
cd mobile
npm run test
```

Manual Phase 9 acceptance is tracked in `docs/testing/mobile-phase-9-manual-checklist.md`.

## Environment

Copy `.env.example` to `.env` for local defaults when needed. Never commit real secrets.

For non-local environments, set `JWT_ALLOW_INSECURE_DEV_SECRET=false` and provide a unique `JWT_SECRET` with at least 32 bytes.

Account deletion is a soft delete with privacy cleanup: active refresh tokens are revoked and stored user/profile identifiers are anonymized so the original email can be reused.

Unauthenticated auth actions are rate limited with `AUTH_RATE_LIMIT_PER_WINDOW` and `AUTH_RATE_LIMIT_WINDOW_SECONDS`; the limiter currently covers registration, login, password reset requests, and password reset confirmations.

Mock subscription purchase APIs are disabled by default. Set `SUBSCRIPTIONS_MOCK_PURCHASES_ENABLED=true` only for local/test flows until real store receipt validation is implemented.

AI story generation uses `AI_PROVIDER=mock` by default so local development and tests do not need external credentials. To use OpenAI for Phase 5 generation, set `AI_PROVIDER=openai`, provide `OPENAI_API_KEY`, and optionally override `AI_OPENAI_MODEL`, `AI_OPENAI_BASE_URL`, `AI_OPENAI_CONNECT_TIMEOUT`, or `AI_OPENAI_READ_TIMEOUT`. The backend sends structured JSON schema requests and still validates, moderates, retries, and records attempts before accepting a story segment. Submit-word attempts and AI generation calls are rate limited with `SUBMIT_WORD_RATE_LIMIT_PER_WINDOW`, `SUBMIT_WORD_RATE_LIMIT_WINDOW_SECONDS`, `AI_GENERATION_RATE_LIMIT_PER_WINDOW`, and `AI_GENERATION_RATE_LIMIT_WINDOW_SECONDS`.

AI observability is available through logs, the `ai_generation_attempts` table, and Micrometer metrics. In local development, expose metrics by adding `metrics` to `management.endpoints.web.exposure.include`, then inspect `ai_generation_attempts_total`, `ai_generation_attempt_duration`, `ai_generation_failures_total`, and `word_similarity_rejections_total` through Actuator.

Private-beta Prometheus query examples for the room/game funnel, AI failures, moderation blocks, random-word requests, and subscription upgrades are documented in `docs/operations/prometheus-queries.md`. Backend image build and staging smoke-test notes are documented in `docs/operations/deployment-readiness.md`.

Basic k6 room/game load-test instructions are documented in `docs/testing/load-test-room-game.md`.

Private-beta security and privacy gates are documented in `docs/operations/security-checklist.md` and `docs/operations/privacy-account-deletion-checklist.md`.

Moderation blocks are persisted to `moderation_events` for private-beta review. Admin users can fetch recent blocked submitted-word and AI-output events at `GET /api/v1/admin/moderation/events`.

Word registry prompt memory is bounded by `WORD_REGISTRY_RECENT_WINDOW_DAYS`; inactive rows are retained during private beta for debugging and tuning. See `docs/operations/word-registry-retention.md` for the Phase 6 retention and future pruning strategy.

## Current Phase

Phase 10 in progress: Hardening and MVP release readiness. Current slices add configurable auth, submit-word, random-word, and AI generation rate limits, durable moderation-event auditing with an admin review endpoint, MVP metrics including active WebSocket connections, trace-ready observations for key room/game/AI/voting flows, documented Prometheus queries, a backend Docker image path with CI release-readiness checks and staging notes, a basic room/game load test, and private-beta security/privacy checklists.
