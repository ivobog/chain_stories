package com.chainreaction.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.room.domain.Room;
import com.chainreaction.room.domain.RoomVisibility;
import com.chainreaction.room.domain.SafetyMode;
import com.chainreaction.room.domain.WritingStyle;
import com.chainreaction.user.domain.User;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class StoryGenerationServiceTests {

    private final UUID gameId = UUID.randomUUID();
    private final UUID turnId = UUID.randomUUID();

    @Test
    void retriesInvalidProviderOutputAndReturnsValidatedResult() {
        AtomicInteger attempts = new AtomicInteger();
        StoryAiProvider provider = (prompt, request) -> {
            if (attempts.incrementAndGet() == 1) {
                return result("A lantern glows in the alley.", "lantern");
            }
            return result("The word \"dragon\" pushes the story into a stranger turn.", "dragon");
        };
        TestStoryGenerationService service = service(provider, 3);

        StoryGenerationResult result = service.service().generate(
                gameId,
                turnId,
                room(SafetyMode.TEEN),
                "Dragon",
                "The story begins.");

        assertThat(result.sentence()).isEqualTo("The word \"dragon\" pushes the story into a stranger turn.");
        assertThat(attempts).hasValue(2);
        assertThat(service.meterRegistry().counter(
                "ai_generation_attempts_total",
                "provider", "custom",
                "status", "failed").count()).isEqualTo(1);
        assertThat(service.meterRegistry().counter(
                "ai_generation_attempts_total",
                "provider", "custom",
                "status", "succeeded").count()).isEqualTo(1);
        verify(service.attemptRecorder()).recordFailure(
                eq(gameId),
                eq(turnId),
                eq("dragon"),
                eq(1),
                eq("custom"),
                eq(result("A lantern glows in the alley.", "lantern")),
                anyLong(),
                eq("AI response did not use the submitted word."));
        verify(service.attemptRecorder()).recordSuccess(
                eq(gameId),
                eq(turnId),
                eq("dragon"),
                eq(2),
                eq("custom"),
                eq(result),
                anyLong());
    }

    @Test
    void stopsAfterConfiguredAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        StoryAiProvider provider = (prompt, request) -> {
            attempts.incrementAndGet();
            return result("A lantern glows in the alley.", "lantern");
        };
        TestStoryGenerationService service = service(provider, 2);

        assertThatThrownBy(() -> service.service().generate(gameId, turnId, room(SafetyMode.TEEN), "dragon", "The story begins."))
                .isInstanceOf(ApiException.class)
                .hasMessage("Story generation failed. Please try again.")
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(com.chainreaction.common.error.ErrorCode.AI_GENERATION_FAILED);
        assertThat(attempts).hasValue(2);
        assertThat(service.meterRegistry().counter(
                "ai_generation_attempts_total",
                "provider", "custom",
                "status", "failed").count()).isEqualTo(2);
        assertThat(service.meterRegistry().counter(
                "ai_generation_failures_total",
                "provider", "custom",
                "reason", "exhausted_retries").count()).isEqualTo(1);
        verify(service.attemptRecorder()).recordFailure(
                eq(gameId),
                eq(turnId),
                eq("dragon"),
                eq(1),
                eq("custom"),
                eq(result("A lantern glows in the alley.", "lantern")),
                anyLong(),
                eq("AI response did not use the submitted word."));
        verify(service.attemptRecorder()).recordFailure(
                eq(gameId),
                eq(turnId),
                eq("dragon"),
                eq(2),
                eq("custom"),
                eq(result("A lantern glows in the alley.", "lantern")),
                anyLong(),
                eq("AI response did not use the submitted word."));
    }

    @Test
    void doesNotRetryInputModerationFailures() {
        AtomicInteger attempts = new AtomicInteger();
        StoryAiProvider provider = (prompt, request) -> {
            attempts.incrementAndGet();
            return result("The word \"dragon\" pushes the story into a stranger turn.", "dragon");
        };
        TestStoryGenerationService service = service(provider, 3);

        assertThatThrownBy(() -> service.service().generate(gameId, turnId, room(SafetyMode.TEEN), "two words", "The story begins."))
                .isInstanceOf(ApiException.class)
                .hasMessage("Submit exactly one word.");
        assertThat(attempts).hasValue(0);
        assertThat(service.meterRegistry().find("ai_generation_attempts_total").counter()).isNull();
        verify(service.attemptRecorder(), never()).recordFailure(
                any(),
                any(),
                any(),
                anyInt(),
                any(),
                any(),
                anyLong(),
                any());
    }

    @Test
    void retriesOutputModerationFailures() {
        AtomicInteger attempts = new AtomicInteger();
        StoryAiProvider provider = (prompt, request) -> {
            if (attempts.incrementAndGet() == 1) {
                return result("The word \"dragon\" brings a murder mystery to town.", "dragon");
            }
            return result("The word \"dragon\" pushes the story into a stranger turn.", "dragon");
        };
        TestStoryGenerationService service = service(provider, 3);

        StoryGenerationResult result = service.service().generate(
                gameId,
                turnId,
                room(SafetyMode.TEEN),
                "dragon",
                "The story begins.");

        assertThat(result.sentence()).isEqualTo("The word \"dragon\" pushes the story into a stranger turn.");
        assertThat(attempts).hasValue(2);
        verify(service.attemptRecorder()).recordFailure(
                eq(gameId),
                eq(turnId),
                eq("dragon"),
                eq(1),
                eq("custom"),
                eq(result("The word \"dragon\" brings a murder mystery to town.", "dragon")),
                anyLong(),
                eq("AI response failed output moderation."));
    }

    private TestStoryGenerationService service(StoryAiProvider provider, int maxAttempts) {
        AiGenerationAttemptRecorder attemptRecorder = mock(AiGenerationAttemptRecorder.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        StoryGenerationService service = new StoryGenerationService(
                new WordModerationService(),
                new StoryPromptBuilder(),
                provider,
                new StoryGenerationValidator(),
                new StoryOutputModerationService(),
                attemptRecorder,
                new AiGenerationMetrics(meterRegistry),
                maxAttempts);
        return new TestStoryGenerationService(service, attemptRecorder, meterRegistry);
    }

    private StoryGenerationResult result(String sentence, String usedWord) {
        return new StoryGenerationResult(
                sentence,
                usedWord,
                "FUNNY",
                2,
                "TEEN",
                "The story turns strange.",
                "Continue the escalation.",
                List.of("mock"),
                "test-model",
                10,
                8);
    }

    private Room room(SafetyMode safetyMode) {
        return new Room(
                "ABC123",
                new User("host@example.com", "hash"),
                WritingStyle.FUNNY,
                "en",
                safetyMode,
                4,
                10,
                30,
                RoomVisibility.PRIVATE);
    }

    private record TestStoryGenerationService(
            StoryGenerationService service,
            AiGenerationAttemptRecorder attemptRecorder,
            SimpleMeterRegistry meterRegistry) {
    }
}
