package com.chainreaction.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;

@Component
public class AuthRateLimiter {

    private static final String MESSAGE = "Too many authentication attempts. Please wait before trying again.";

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final Duration window;

    public AuthRateLimiter(
            @Value("${app.security.auth.rate-limit-per-window:5}") int limit,
            @Value("${app.security.auth.rate-limit-window-seconds:60}") long windowSeconds) {
        this.limit = Math.max(1, limit);
        this.window = Duration.ofSeconds(Math.max(1, windowSeconds));
    }

    public void checkRegistrationAllowed(String email) {
        checkAllowed("register", email);
    }

    public void checkLoginAllowed(String email) {
        checkAllowed("login", email);
    }

    public void checkPasswordResetRequestAllowed(String email) {
        checkAllowed("password-reset-request", email);
    }

    public void checkPasswordResetConfirmAllowed(String resetTokenHash) {
        checkAllowed("password-reset-confirm", resetTokenHash);
    }

    private synchronized void checkAllowed(String action, String identifier) {
        Instant now = Instant.now();
        windows.entrySet().removeIf(entry -> !entry.getValue().startedAt.plus(window).isAfter(now));

        String key = action + ":" + normalize(identifier);
        Window current = windows.get(key);
        if (current == null) {
            windows.put(key, new Window(now, 1));
            return;
        }
        if (current.count >= limit) {
            throw new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS, MESSAGE);
        }
        windows.put(key, new Window(current.startedAt, current.count + 1));
    }

    private String normalize(String identifier) {
        return identifier.trim().toLowerCase(Locale.ROOT);
    }

    private record Window(Instant startedAt, int count) {
    }
}
