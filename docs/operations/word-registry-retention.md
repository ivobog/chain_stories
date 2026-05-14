# Word Registry Retention

The Phase 6 word registry stores accepted word usages so story generation can avoid repeating recent jokes, images, and twists.

## Active Prompt Memory

Prompt memory is intentionally bounded. Story generation only reads recent registry entries for the same room, normalized word, writing style, and language.

The default active memory window is 30 days and is controlled by:

```text
WORD_REGISTRY_RECENT_WINDOW_DAYS=30
```

Rows older than this window are not included in prompts or similarity checks.

## Storage Policy

For the private-beta backend, registry rows are retained after they leave the active prompt-memory window. Retaining older rows keeps enough history for debugging, anti-repetition tuning, abuse review, and product analysis while the scoring thresholds are still evolving.

The registry does not need a separate archive table in Phase 6 because inactive rows are already excluded from runtime prompt memory by the `created_at` cutoff and indexed lookup.

## Future Pruning Job

Before public launch, add a scheduled retention job that either deletes or archives inactive registry rows after a configured hard-retention period.

Recommended first production policy:

- Keep active prompt memory at 30 days.
- Keep inactive registry rows for 180 days unless a stricter privacy requirement applies.
- Delete rows for deleted/anonymized users when account-deletion workflows are expanded to story-memory data.
- Emit pruning metrics for deleted row count and job failures.

The expected operational query shape for pruning is:

```sql
DELETE FROM word_registry_entries
WHERE created_at < now() - interval '180 days';
```

If product analytics later needs longer aggregate history, archive only anonymized, aggregated counts by normalized word, style, language, and month. Do not archive player ids, turn ids, segment ids, or generated sentences unless a privacy review explicitly approves that data class.
