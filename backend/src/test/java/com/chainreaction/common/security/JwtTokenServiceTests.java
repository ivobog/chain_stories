package com.chainreaction.common.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class JwtTokenServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsPlaceholderSecretWhenDevSecretIsNotAllowed() {
        JwtTokenService jwtTokenService = new JwtTokenService(
                objectMapper,
                "chain-stories-test",
                "replace-with-local-development-secret",
                false,
                15);

        assertThrows(IllegalStateException.class, jwtTokenService::validateConfiguration);
    }

    @Test
    void acceptsLongSecretWhenDevSecretIsNotAllowed() {
        JwtTokenService jwtTokenService = new JwtTokenService(
                objectMapper,
                "chain-stories-test",
                "a-long-enough-secret-for-tests-123456789",
                false,
                15);

        assertDoesNotThrow(jwtTokenService::validateConfiguration);
    }

    @Test
    void rejectsNonPositiveAccessTokenTtl() {
        JwtTokenService jwtTokenService = new JwtTokenService(
                objectMapper,
                "chain-stories-test",
                "a-long-enough-secret-for-tests-123456789",
                true,
                0);

        assertThrows(IllegalStateException.class, jwtTokenService::validateConfiguration);
    }
}
