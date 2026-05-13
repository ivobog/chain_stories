package com.chainreaction.ai;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_generation_attempts")
public class AiGenerationAttempt {

    @Id
    private UUID id;

    @Column(name = "game_id")
    private UUID gameId;

    @Column(name = "turn_id")
    private UUID turnId;

    @Column(name = "normalized_word", nullable = false, length = 80)
    private String normalizedWord;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiGenerationAttemptStatus status;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(length = 128)
    private String model;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiGenerationAttempt() {
    }

    public AiGenerationAttempt(
            UUID gameId,
            UUID turnId,
            String normalizedWord,
            int attemptNumber,
            AiGenerationAttemptStatus status,
            String provider,
            String model,
            Integer promptTokens,
            Integer completionTokens,
            long latencyMs,
            String failureReason) {
        this.id = UUID.randomUUID();
        this.gameId = gameId;
        this.turnId = turnId;
        this.normalizedWord = normalizedWord;
        this.attemptNumber = attemptNumber;
        this.status = status;
        this.provider = provider;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.latencyMs = latencyMs;
        this.failureReason = failureReason == null ? null : failureReason.substring(0, Math.min(512, failureReason.length()));
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getGameId() {
        return gameId;
    }

    public UUID getTurnId() {
        return turnId;
    }

    public String getNormalizedWord() {
        return normalizedWord;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public AiGenerationAttemptStatus getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
