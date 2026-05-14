# Docker

The root `docker-compose.yml` starts PostgreSQL and Redis by default. The backend image is available behind the `app` profile so dependency-only workflows remain fast.

Build and start the backend with its local dependencies:

```powershell
docker compose --profile app up -d --build backend
```

Check readiness:

```powershell
docker compose ps
```

```powershell
curl http://localhost:8080/actuator/health/readiness
```

Stop all local containers:

```powershell
docker compose --profile app down
```

Production-like environments should override at least `JWT_SECRET`, `JWT_ALLOW_INSECURE_DEV_SECRET=false`, datasource credentials, Redis host/port, and AI provider credentials. The backend image listens on port `8080` and exposes Actuator health endpoints for container health checks.

Future artifacts may include:

- Mobile build notes.
- Observability compose overrides.
- Deployment-specific container configuration.
