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

Validation:

- Passwords must be 8 to 128 characters and include uppercase, lowercase, number, and symbol characters.
- Validation failures use `errorCode: VALIDATION_FAILED` and include `fieldErrors`.

## Planned Artifacts

- OpenAPI specification for `/api/v1`.
- WebSocket event envelope and event payload schemas.
- Standard error response examples.
- Mobile API contract notes.
