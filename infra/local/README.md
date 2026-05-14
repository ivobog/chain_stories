# Local Infrastructure

Phase 0 local infrastructure is intentionally small:

- PostgreSQL for durable application state.
- Redis for active room state, locks, rate limits, and future WebSocket pub/sub.

Start services from the repository root:

```powershell
docker compose up -d
```

Check status:

```powershell
docker compose ps
```

Stop services:

```powershell
docker compose down
```

The named Docker volumes are `postgres-data` and `redis-data`.

To run the backend in Docker as well:

```powershell
docker compose --profile app up -d --build backend
```
