package com.chainreaction.ai;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class AiGenerationMetrics {

    private final MeterRegistry meterRegistry;

    public AiGenerationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAttempt(String provider, AiGenerationAttemptStatus status, long latencyMs) {
        String statusTag = status.name().toLowerCase();
        Counter.builder("ai_generation_attempts_total")
                .description("AI generation provider attempts.")
                .tag("provider", provider)
                .tag("status", statusTag)
                .register(meterRegistry)
                .increment();
        Timer.builder("ai_generation_attempt_duration")
                .description("AI generation provider attempt duration.")
                .tag("provider", provider)
                .tag("status", statusTag)
                .register(meterRegistry)
                .record(latencyMs, TimeUnit.MILLISECONDS);
    }

    public void recordExhaustedRetries(String provider) {
        Counter.builder("ai_generation_failures_total")
                .description("AI generations that exhausted retry attempts before producing an accepted segment.")
                .tag("provider", provider)
                .tag("reason", "exhausted_retries")
                .register(meterRegistry)
                .increment();
    }

    public void recordWordSimilarityRejection(String provider, String writingStyle, String language) {
        Counter.builder("word_similarity_rejections_total")
                .description("Generated outputs rejected because they were too similar to previous word usage.")
                .tag("provider", provider)
                .tag("writing_style", writingStyle.toLowerCase())
                .tag("language", language)
                .register(meterRegistry)
                .increment();
    }
}
