package com.chainreaction.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.chainreaction.moderation.ModerationEventSource;
import com.chainreaction.room.domain.SafetyMode;
import com.chainreaction.room.domain.WritingStyle;
import com.chainreaction.subscription.domain.SubscriptionPlan;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ApplicationMetricsTests {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ApplicationMetrics metrics = new ApplicationMetrics(meterRegistry);

    @Test
    void recordsRoomAndGameLifecycleCountersWithTags() {
        metrics.recordRoomCreated(WritingStyle.FUNNY, "en", SafetyMode.TEEN);
        metrics.recordGameStarted(WritingStyle.FUNNY, "en", SafetyMode.TEEN);
        metrics.recordGameFinished();

        assertThat(meterRegistry.counter(
                "rooms_created_total",
                "writing_style", "funny",
                "language", "en",
                "safety_mode", "teen").count()).isEqualTo(1);
        assertThat(meterRegistry.counter(
                "games_started_total",
                "writing_style", "funny",
                "language", "en",
                "safety_mode", "teen").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("games_finished_total").count()).isEqualTo(1);
    }

    @Test
    void recordsGameplayModerationAndSubscriptionCounters() {
        metrics.recordRandomWordRequest(WritingStyle.HORROR, "de", SafetyMode.FAMILY);
        metrics.recordModerationBlock(ModerationEventSource.SUBMITTED_WORD, SafetyMode.FAMILY);
        metrics.recordSubscriptionUpgrade(SubscriptionPlan.PLUS);

        assertThat(meterRegistry.counter(
                "random_word_requests_total",
                "writing_style", "horror",
                "language", "de",
                "safety_mode", "family").count()).isEqualTo(1);
        assertThat(meterRegistry.counter(
                "moderation_blocks_total",
                "source", "submitted_word",
                "safety_mode", "family").count()).isEqualTo(1);
        assertThat(meterRegistry.counter(
                "subscription_upgrades_total",
                "plan", "plus").count()).isEqualTo(1);
    }
}
