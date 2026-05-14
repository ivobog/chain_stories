package com.chainreaction.game.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;

class SubmitWordRateLimiterTests {

    @Test
    void rejectsSubmitAttemptsAfterConfiguredLimit() {
        SubmitWordRateLimiter limiter = new SubmitWordRateLimiter(2, 60);
        UUID userId = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();

        assertThatCode(() -> limiter.checkAllowed(userId, gameId, turnId)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkAllowed(userId, gameId, turnId)).doesNotThrowAnyException();

        assertThatThrownBy(() -> limiter.checkAllowed(userId, gameId, turnId))
                .isInstanceOf(ApiException.class)
                .hasMessage("Submit word rate limit exceeded. Please wait before trying again.")
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void tracksTurnsIndependently() {
        SubmitWordRateLimiter limiter = new SubmitWordRateLimiter(1, 60);
        UUID userId = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();

        limiter.checkAllowed(userId, gameId, UUID.randomUUID());

        assertThatCode(() -> limiter.checkAllowed(userId, gameId, UUID.randomUUID())).doesNotThrowAnyException();
    }
}
