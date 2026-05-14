# Security Checklist

Use this checklist before opening a private beta environment.

## Secrets And Configuration

- Set `JWT_ALLOW_INSECURE_DEV_SECRET=false` outside local development.
- Use a unique `JWT_SECRET` with at least 32 bytes for each environment.
- Keep `OPENAI_API_KEY` and future store credentials out of Git and mobile bundles.
- Keep `SUBSCRIPTIONS_MOCK_PURCHASES_ENABLED=false` outside local/test environments.
- Expose `health,info` publicly at most; expose `metrics` or `prometheus` only on a private monitoring network.

## Authentication

- Confirm registration, login, password-reset request, and password-reset confirm rate limits are configured.
- Confirm refresh-token rotation is enabled and replaying an old refresh token returns `INVALID_REFRESH_TOKEN`.
- Confirm logout revokes the submitted refresh token.
- Confirm password reset revokes all active refresh tokens for the user.
- Confirm deleted and suspended accounts cannot authenticate.

## Gameplay Abuse Controls

- Confirm random-word, submit-word, and AI-generation rate limits are configured.
- Confirm only the current player can submit a word or request a random word.
- Confirm only room hosts can start games, close rooms, kick players, and edit lobby settings.
- Confirm WebSocket room-topic subscriptions reject users who are not active room participants.

## Admin And Moderation

- Confirm `/api/v1/admin/**` requires `ROLE_ADMIN`.
- Confirm blocked submitted words and blocked AI output create `moderation_events`.
- Review moderation event access before assigning `ROLE_ADMIN` in production data.
- Confirm moderation review does not expose refresh tokens, password hashes, or full account credentials.

## Deployment

- Confirm `/actuator/health/liveness` and `/actuator/health/readiness` return `UP`.
- Confirm Flyway validates all migrations during startup.
- Run the room/game k6 load-test smoke script before inviting beta users.
- Review logs for correlation ids on API errors before triage starts.
