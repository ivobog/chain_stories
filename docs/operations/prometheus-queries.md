# Prometheus Queries

Phase 10 exposes MVP counters for private-beta debugging. In local development, expose metrics with `management.endpoints.web.exposure.include=health,info,metrics,prometheus` before scraping Actuator.

## Room And Game Funnel

```promql
sum(increase(rooms_created_total[1h]))
```

```promql
sum(increase(games_started_total[1h]))
```

```promql
sum(increase(games_finished_total[1h]))
```

```promql
sum(increase(games_started_total[1h])) / clamp_min(sum(increase(rooms_created_total[1h])), 1)
```

```promql
sum(increase(games_finished_total[1h])) / clamp_min(sum(increase(games_started_total[1h])), 1)
```

## AI And Moderation

```promql
sum(rate(ai_generation_attempts_total[5m])) by (provider, status)
```

```promql
histogram_quantile(0.95, sum(rate(ai_generation_attempt_duration_seconds_bucket[5m])) by (le, provider))
```

```promql
sum(increase(ai_generation_failures_total[1h])) by (provider, reason)
```

```promql
sum(increase(moderation_blocks_total[1h])) by (source, safety_mode)
```

```promql
sum(increase(word_similarity_rejections_total[1h])) by (provider, writing_style, language)
```

## Gameplay And Subscription Signals

```promql
sum(increase(random_word_requests_total[1h])) by (writing_style, language, safety_mode)
```

```promql
websocket_connections_active
```

```promql
sum(increase(subscription_upgrades_total[24h])) by (plan)
```

## Trace-Ready Flows

Phase 10 wraps the private-beta flows below in Micrometer observations. When the deployment enables an OpenTelemetry bridge/exporter, these observation names become trace spans:

- `room.create`
- `room.join`
- `game.start`
- `game.submit_word`
- `game.random_word`
- `ai.story_generation`
- `vote.submit`
