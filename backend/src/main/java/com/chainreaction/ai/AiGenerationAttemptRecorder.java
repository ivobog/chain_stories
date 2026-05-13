package com.chainreaction.ai;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiGenerationAttemptRecorder {

    private final AiGenerationAttemptRepository repository;

    public AiGenerationAttemptRecorder(AiGenerationAttemptRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(
            UUID gameId,
            UUID turnId,
            String normalizedWord,
            int attemptNumber,
            String provider,
            StoryGenerationResult result,
            long latencyMs) {
        repository.save(new AiGenerationAttempt(
                gameId,
                turnId,
                normalizedWord,
                attemptNumber,
                AiGenerationAttemptStatus.SUCCEEDED,
                provider,
                result.model(),
                result.promptTokens(),
                result.completionTokens(),
                latencyMs,
                null));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            UUID gameId,
            UUID turnId,
            String normalizedWord,
            int attemptNumber,
            String provider,
            StoryGenerationResult result,
            long latencyMs,
            String failureReason) {
        repository.save(new AiGenerationAttempt(
                gameId,
                turnId,
                normalizedWord,
                attemptNumber,
                AiGenerationAttemptStatus.FAILED,
                provider,
                result == null ? null : result.model(),
                result == null ? null : result.promptTokens(),
                result == null ? null : result.completionTokens(),
                latencyMs,
                failureReason));
    }
}
