# Deployment Readiness

Phase 10 private-beta deployment assumes one backend container, PostgreSQL, Redis, and a mobile app pointed at the backend API.

## Build

```powershell
docker compose --profile app build backend
```

The backend image is built from `backend/Dockerfile` using Java 21. The runtime image exposes port `8080` and starts the Spring Boot jar.

CI validates the same release path by checking the Compose `app` profile, building the backend Docker image, and parsing the load-test script.

## Required Environment

Set these values per environment:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATA_REDIS_HOST`
- `SPRING_DATA_REDIS_PORT`
- `JWT_ISSUER`
- `JWT_SECRET`
- `JWT_ALLOW_INSECURE_DEV_SECRET=false`
- `AI_PROVIDER`
- `OPENAI_API_KEY` when `AI_PROVIDER=openai`

Private-beta tuning knobs:

- `AUTH_RATE_LIMIT_PER_WINDOW`
- `AUTH_RATE_LIMIT_WINDOW_SECONDS`
- `SUBMIT_WORD_RATE_LIMIT_PER_WINDOW`
- `SUBMIT_WORD_RATE_LIMIT_WINDOW_SECONDS`
- `AI_GENERATION_RATE_LIMIT_PER_WINDOW`
- `AI_GENERATION_RATE_LIMIT_WINDOW_SECONDS`
- `AI_SUGGESTION_RATE_LIMIT_PER_WINDOW`
- `AI_SUGGESTION_RATE_LIMIT_WINDOW_SECONDS`
- `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE`

## Health Checks

Use Actuator probes:

```text
GET /actuator/health/liveness
GET /actuator/health/readiness
```

Expose only `health,info` publicly. Expose `metrics` or `prometheus` only to the private monitoring network.

## Staging Smoke Test

1. Apply migrations by starting the backend against staging PostgreSQL.
2. Confirm `GET /actuator/health/readiness` returns `UP`.
3. Register a test user.
4. Create a two-player private room.
5. Start a game.
6. Submit one accepted word.
7. Request one random word.
8. Submit a blocked word and confirm a moderation event appears for an admin.
9. Complete voting and confirm the game reaches `FINISHED`.
10. Check the Prometheus queries in `docs/operations/prometheus-queries.md` for non-zero room/game counters.

## Rollback

For private beta, rollback means redeploying the previous backend image while keeping PostgreSQL data in place. Do not delete Docker volumes or database schemas during rollback.
