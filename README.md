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

## Mobile

```powershell
cd mobile
npm install
npm run start
```

The mobile app is currently a Phase 0 placeholder screen. Real authentication and room/game screens start in later phases.

## Environment

Copy `.env.example` to `.env` for local defaults when needed. Never commit real secrets.

For non-local environments, set `JWT_ALLOW_INSECURE_DEV_SECRET=false` and provide a unique `JWT_SECRET` with at least 32 bytes.

Account deletion is a soft delete with privacy cleanup: active refresh tokens are revoked and stored user/profile identifiers are anonymized so the original email can be reused.

## Current Phase

Phase 0: repository foundation, local infrastructure, backend shell, mobile shell, CI, and architecture decision records.
