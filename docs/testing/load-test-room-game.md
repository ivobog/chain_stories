# Room/Game Load Test

Phase 10 includes basic k6 scripts for the private-beta room/game flow and the play-with-bot flow.

## Prerequisites

- Backend reachable from the machine running k6.
- PostgreSQL and Redis available to the backend.
- `AI_PROVIDER=mock` for baseline load tests unless intentionally testing provider latency.
- k6 installed locally or available in CI.

## Run

Start the backend locally:

```powershell
docker compose --profile app up -d --build backend
```

Run the test:

```powershell
k6 run tools/load/k6-room-game.mjs
```

Run the play-with-bot smoke test:

```powershell
k6 run tools/load/k6-play-with-bot-game.mjs
```

Override target and volume:

```powershell
$env:BACKEND_URL="http://localhost:8080"
$env:K6_VUS="5"
$env:K6_ITERATIONS="20"
k6 run tools/load/k6-room-game.mjs
```

## Covered Flow

Each iteration:

1. Registers a host and player.
2. Creates a private two-player room.
3. Joins the player to the room.
4. Starts a two-turn game.
5. Requests one random word.
6. Submits one word per player.
7. Submits all vote categories for both players.
8. Fetches vote results.

The play-with-bot script:

1. Registers one human player.
2. Creates a two-turn play-with-bot game.
3. Submits the human word.
4. Polls until the async bot turn is auto-submitted.
5. Verifies the game reaches voting with three story segments.

## Baseline Thresholds

The script currently fails if:

- HTTP failed request rate is `>= 5%`.
- p95 HTTP request duration is `>= 1500ms`.

These are private-beta smoke thresholds, not final production SLOs.
