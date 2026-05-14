package com.chainreaction.ai;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;
import com.chainreaction.moderation.ModerationAuditService;
import com.chainreaction.moderation.ModerationEventSource;
import com.chainreaction.observability.ApplicationObservations;
import com.chainreaction.room.domain.Room;
import com.chainreaction.word.WordRegistryService;

import io.micrometer.common.KeyValue;

@Service
public class StoryGenerationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoryGenerationService.class);

    private final WordModerationService wordModerationService;
    private final StoryPromptBuilder promptBuilder;
    private final StoryAiProvider aiProvider;
    private final StoryGenerationValidator validator;
    private final StoryOutputModerationService outputModerationService;
    private final StorySimilarityService similarityService;
    private final AiGenerationAttemptRecorder attemptRecorder;
    private final AiGenerationMetrics metrics;
    private final WordRegistryService wordRegistryService;
    private final AiGenerationRateLimiter aiGenerationRateLimiter;
    private final ModerationAuditService moderationAuditService;
    private final ApplicationObservations applicationObservations;
    private final int maxAttempts;

    public StoryGenerationService(
            WordModerationService wordModerationService,
            StoryPromptBuilder promptBuilder,
            StoryAiProvider aiProvider,
            StoryGenerationValidator validator,
            StoryOutputModerationService outputModerationService,
            StorySimilarityService similarityService,
            AiGenerationAttemptRecorder attemptRecorder,
            AiGenerationMetrics metrics,
            WordRegistryService wordRegistryService,
            AiGenerationRateLimiter aiGenerationRateLimiter,
            ModerationAuditService moderationAuditService,
            ApplicationObservations applicationObservations,
            @Value("${app.ai.generation.max-attempts:3}") int maxAttempts) {
        this.wordModerationService = wordModerationService;
        this.promptBuilder = promptBuilder;
        this.aiProvider = aiProvider;
        this.validator = validator;
        this.outputModerationService = outputModerationService;
        this.similarityService = similarityService;
        this.attemptRecorder = attemptRecorder;
        this.metrics = metrics;
        this.wordRegistryService = wordRegistryService;
        this.aiGenerationRateLimiter = aiGenerationRateLimiter;
        this.moderationAuditService = moderationAuditService;
        this.applicationObservations = applicationObservations;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public StoryGenerationResult generate(
            UUID gameId,
            UUID turnId,
            UUID playerUserId,
            Room room,
            String word,
            String currentStory) {
        return applicationObservations.observe(
                "ai.story_generation",
                () -> generateObserved(gameId, turnId, playerUserId, room, word, currentStory),
                KeyValue.of("provider", aiProvider.providerName()),
                KeyValue.of("writing_style", room.getWritingStyle().name().toLowerCase()),
                KeyValue.of("language", room.getLanguage()),
                KeyValue.of("safety_mode", room.getSafetyMode().name().toLowerCase()));
    }

    private StoryGenerationResult generateObserved(
            UUID gameId,
            UUID turnId,
            UUID playerUserId,
            Room room,
            String word,
            String currentStory) {
        aiGenerationRateLimiter.checkAllowed(gameId, turnId);
        ModeratedWord moderatedWord;
        try {
            moderatedWord = wordModerationService.moderate(word, room.getSafetyMode());
        } catch (ApiException exception) {
            moderationAuditService.recordBlocked(
                    gameId,
                    room.getId(),
                    turnId,
                    playerUserId,
                    ModerationEventSource.SUBMITTED_WORD,
                    room.getSafetyMode(),
                    exception.getMessage(),
                    word);
            throw exception;
        }
        StoryGenerationRequest request = new StoryGenerationRequest(
                moderatedWord.normalized(),
                room.getWritingStyle(),
                room.getLanguage(),
                room.getSafetyMode(),
                currentStory,
                wordRegistryService.recentUsagesForPrompt(
                        room.getId(),
                        moderatedWord.normalized(),
                        room.getWritingStyle(),
                        room.getLanguage()));
        StoryGenerationPrompt prompt = promptBuilder.build(request);
        Instant startedAt = Instant.now();
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            StoryGenerationResult result = null;
            long attemptStartedAt = System.nanoTime();
            try {
                result = aiProvider.generate(prompt, request);
                validator.validate(result, request);
                outputModerationService.moderate(result, request);
                similarityService.rejectIfTooSimilar(result, request);
                long latencyMs = elapsedMillis(attemptStartedAt);
                attemptRecorder.recordSuccess(
                        gameId,
                        turnId,
                        moderatedWord.normalized(),
                        attempt,
                        aiProvider.providerName(),
                        result,
                        latencyMs);
                metrics.recordAttempt(aiProvider.providerName(), AiGenerationAttemptStatus.SUCCEEDED, latencyMs);
                LOGGER.info(
                        "story_generation_succeeded model={} promptTokens={} completionTokens={} attempts={} latencyMs={}",
                        result.model(),
                        result.promptTokens(),
                        result.completionTokens(),
                        attempt,
                        latencyMs);
                return result;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                long latencyMs = elapsedMillis(attemptStartedAt);
                attemptRecorder.recordFailure(
                        gameId,
                        turnId,
                        moderatedWord.normalized(),
                        attempt,
                        aiProvider.providerName(),
                        result,
                        latencyMs,
                        exception.getMessage());
                metrics.recordAttempt(aiProvider.providerName(), AiGenerationAttemptStatus.FAILED, latencyMs);
                if (exception instanceof StorySimilarityRejectionException) {
                    metrics.recordWordSimilarityRejection(
                            aiProvider.providerName(),
                            room.getWritingStyle().name(),
                            room.getLanguage());
                }
                if (result != null && exception instanceof ApiException apiException
                        && apiException.getStatus().is5xxServerError()) {
                    moderationAuditService.recordBlocked(
                            gameId,
                            room.getId(),
                            turnId,
                            playerUserId,
                            ModerationEventSource.AI_OUTPUT,
                            room.getSafetyMode(),
                            exception.getMessage(),
                            result.sentence());
                }
                LOGGER.warn(
                        "story_generation_attempt_failed attempt={} maxAttempts={} latencyMs={} reason={}",
                        attempt,
                        maxAttempts,
                        latencyMs,
                        exception.getMessage());
            }
        }
        LOGGER.warn(
                "story_generation_failed attempts={} latencyMs={} reason={}",
                maxAttempts,
                Duration.between(startedAt, Instant.now()).toMillis(),
                lastFailure == null ? "unknown" : lastFailure.getMessage());
        metrics.recordExhaustedRetries(aiProvider.providerName());
        throw new ApiException(ErrorCode.AI_GENERATION_FAILED, HttpStatus.BAD_GATEWAY,
                "Story generation failed. Please try again.");
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0, Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis());
    }
}
