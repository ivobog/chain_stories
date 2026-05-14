package com.chainreaction.ai;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;

class AiGenerationRateLimiterTests {

    @Test
    void rejectsGenerationAfterConfiguredLimit() {
        AiGenerationRateLimiter limiter = new AiGenerationRateLimiter(2, 60);
        UUID gameId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();

        assertThatCode(() -> limiter.checkAllowed(gameId, turnId)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkAllowed(gameId, turnId)).doesNotThrowAnyException();

        assertThatThrownBy(() -> limiter.checkAllowed(gameId, turnId))
                .isInstanceOf(ApiException.class)
                .hasMessage("AI generation rate limit exceeded. Please wait before trying again.")
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void tracksTurnsIndependently() {
        AiGenerationRateLimiter limiter = new AiGenerationRateLimiter(1, 60);
        UUID gameId = UUID.randomUUID();

        limiter.checkAllowed(gameId, UUID.randomUUID());

        assertThatCode(() -> limiter.checkAllowed(gameId, UUID.randomUUID())).doesNotThrowAnyException();
    }
}
