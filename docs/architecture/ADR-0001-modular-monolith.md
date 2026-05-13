# ADR-0001: Start With a Modular Monolith

Date: 2026-05-13

## Status

Accepted

## Context

The product needs real-time multiplayer gameplay, AI orchestration, moderation, subscriptions, story persistence, and later media generation. The source design documents recommend avoiding premature microservices for the MVP.

## Decision

Build the backend as a Spring Boot modular monolith with clear domain packages. Use REST for request/response APIs, WebSocket events for live game state, PostgreSQL as the source of truth, and Redis for active room state, locks, pub/sub, and rate limits.

## Consequences

- Faster MVP development and local debugging.
- Strong domain boundaries without operational overhead.
- Future extraction remains possible for AI orchestration, media generation, notifications, moderation, and analytics.
