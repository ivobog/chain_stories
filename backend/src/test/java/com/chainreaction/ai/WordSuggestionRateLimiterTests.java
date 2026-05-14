package com.chainreaction.ai;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;

class WordSuggestionRateLimiterTests {

    @Test
    void rejectsRequestsAfterConfiguredLimit() {
        WordSuggestionRateLimiter limiter = new WordSuggestionRateLimiter(2, 60);
        UUID userId = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();

        assertThatCode(() -> limiter.checkAllowed(userId, gameId)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkAllowed(userId, gameId)).doesNotThrowAnyException();

        assertThatThrownBy(() -> limiter.checkAllowed(userId, gameId))
                .isInstanceOf(ApiException.class)
                .hasMessage("Random word suggestion rate limit exceeded. Please wait before trying again.")
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void tracksUsersAndGamesIndependently() {
        WordSuggestionRateLimiter limiter = new WordSuggestionRateLimiter(1, 60);
        UUID userId = UUID.randomUUID();
        UUID firstGameId = UUID.randomUUID();
        UUID secondGameId = UUID.randomUUID();

        limiter.checkAllowed(userId, firstGameId);

        assertThatCode(() -> limiter.checkAllowed(userId, secondGameId)).doesNotThrowAnyException();
    }
}
