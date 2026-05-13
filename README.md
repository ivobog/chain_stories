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

The mobile app still shows a placeholder screen, but `mobile/src/api` now has typed REST and STOMP WebSocket clients ready for auth, room resume, room lifecycle, game, and realtime Phase 8 screens.

## Environment

Copy `.env.example` to `.env` for local defaults when needed. Never commit real secrets.

For non-local environments, set `JWT_ALLOW_INSECURE_DEV_SECRET=false` and provide a unique `JWT_SECRET` with at least 32 bytes.

Account deletion is a soft delete with privacy cleanup: active refresh tokens are revoked and stored user/profile identifiers are anonymized so the original email can be reused.

Mock subscription purchase APIs are disabled by default. Set `SUBSCRIPTIONS_MOCK_PURCHASES_ENABLED=true` only for local/test flows until real store receipt validation is implemented.

## Current Phase

Phase 4 in progress: WebSocket multiplayer. The backend exposes `/ws/game`, authenticates STOMP connections with JWT bearer tokens, authorizes room-topic and user-queue subscriptions, publishes room/game events to `/topic/rooms/{roomId}`, sends private kick events on `/user/queue/events`, has integration coverage for live room-topic and private user-queue delivery, supports reconnect state recovery through room resume and `GET /api/v1/games/{gameId}`, and the mobile app has reusable REST/WebSocket client modules.
