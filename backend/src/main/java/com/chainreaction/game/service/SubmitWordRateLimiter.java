package com.chainreaction.game.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;

@Component
public class SubmitWordRateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final Duration window;

    public SubmitWordRateLimiter(
            @Value("${app.game.submit-word.rate-limit-per-window:5}") int limit,
            @Value("${app.game.submit-word.rate-limit-window-seconds:60}") long windowSeconds) {
        this.limit = Math.max(1, limit);
        this.window = Duration.ofSeconds(Math.max(1, windowSeconds));
    }

    public synchronized void checkAllowed(UUID userId, UUID gameId, UUID turnId) {
        String key = userId + ":" + gameId + ":" + turnId;
        Instant now = Instant.now();
        Window current = windows.get(key);
        if (current == null || !current.startedAt.plus(window).isAfter(now)) {
            windows.put(key, new Window(now, 1));
            return;
        }
        if (current.count >= limit) {
            throw new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
                    "Submit word rate limit exceeded. Please wait before trying again.");
        }
        windows.put(key, new Window(current.startedAt, current.count + 1));
    }

    private record Window(Instant startedAt, int count) {
    }
}
