# ADR-0002: Phase 0 Technology Stack

Date: 2026-05-13

## Status

Accepted

## Decision

Phase 0 uses:

- Java 21 and Spring Boot 3.x for the backend.
- Maven for backend builds.
- PostgreSQL 16 for persistence.
- Redis 7 for live state support.
- Flyway for database migrations.
- React Native with Expo and TypeScript for the mobile app.
- Docker Compose for local infrastructure.
- GitHub Actions for continuous integration.

## Notes

The AI provider remains abstracted and configurable. Phase 0 defaults to a mock provider setting in `.env.example`; real provider integration starts in the AI story loop phase.
