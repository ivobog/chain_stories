package com.chainreaction.observability;

import org.springframework.stereotype.Component;

import com.chainreaction.moderation.ModerationEventSource;
import com.chainreaction.room.domain.SafetyMode;
import com.chainreaction.room.domain.WritingStyle;
import com.chainreaction.subscription.domain.SubscriptionPlan;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class ApplicationMetrics {

    private final MeterRegistry meterRegistry;

    public ApplicationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRoomCreated(WritingStyle writingStyle, String language, SafetyMode safetyMode) {
        Counter.builder("rooms_created_total")
                .description("Rooms created by players.")
                .tag("writing_style", tag(writingStyle))
                .tag("language", language)
                .tag("safety_mode", tag(safetyMode))
                .register(meterRegistry)
                .increment();
    }

    public void recordGameStarted(WritingStyle writingStyle, String language, SafetyMode safetyMode) {
        Counter.builder("games_started_total")
                .description("Games started from lobby rooms.")
                .tag("writing_style", tag(writingStyle))
                .tag("language", language)
                .tag("safety_mode", tag(safetyMode))
                .register(meterRegistry)
                .increment();
    }

    public void recordGameFinished() {
        Counter.builder("games_finished_total")
                .description("Games that reached finished state.")
                .register(meterRegistry)
                .increment();
    }

    public void recordRandomWordRequest(WritingStyle writingStyle, String language, SafetyMode safetyMode) {
        Counter.builder("random_word_requests_total")
                .description("Random word suggestion requests accepted for processing.")
                .tag("writing_style", tag(writingStyle))
                .tag("language", language)
                .tag("safety_mode", tag(safetyMode))
                .register(meterRegistry)
                .increment();
    }

    public void recordModerationBlock(ModerationEventSource source, SafetyMode safetyMode) {
        Counter.builder("moderation_blocks_total")
                .description("Content blocked by moderation.")
                .tag("source", tag(source))
                .tag("safety_mode", tag(safetyMode))
                .register(meterRegistry)
                .increment();
    }

    public void recordSubscriptionUpgrade(SubscriptionPlan plan) {
        Counter.builder("subscription_upgrades_total")
                .description("Subscription upgrades accepted by the backend.")
                .tag("plan", tag(plan))
                .register(meterRegistry)
                .increment();
    }

    private String tag(Enum<?> value) {
        return value.name().toLowerCase();
    }
}
