package com.chainreaction.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;

class AuthRateLimiterTests {

    @Test
    void rejectsAuthenticationAttemptsAfterConfiguredLimit() {
        AuthRateLimiter limiter = new AuthRateLimiter(2, 60);
        String email = "player@example.com";

        assertThatCode(() -> limiter.checkLoginAllowed(email)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkLoginAllowed(email)).doesNotThrowAnyException();

        assertThatThrownBy(() -> limiter.checkLoginAllowed(email))
                .isInstanceOf(ApiException.class)
                .hasMessage("Too many authentication attempts. Please wait before trying again.")
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void tracksAuthActionsIndependently() {
        AuthRateLimiter limiter = new AuthRateLimiter(1, 60);
        String email = "player@example.com";

        limiter.checkLoginAllowed(email);

        assertThatCode(() -> limiter.checkRegistrationAllowed(email)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkPasswordResetRequestAllowed(email)).doesNotThrowAnyException();
    }
}
