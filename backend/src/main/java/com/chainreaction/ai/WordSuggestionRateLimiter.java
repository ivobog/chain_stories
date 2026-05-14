package com.chainreaction.ai;

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
public class WordSuggestionRateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final Duration window;

    public WordSuggestionRateLimiter(
            @Value("${app.ai.suggestions.rate-limit-per-window:3}") int limit,
            @Value("${app.ai.suggestions.rate-limit-window-seconds:60}") long windowSeconds) {
        this.limit = Math.max(1, limit);
        this.window = Duration.ofSeconds(Math.max(1, windowSeconds));
    }

    public synchronized void checkAllowed(UUID userId, UUID gameId) {
        String key = userId + ":" + gameId;
        Instant now = Instant.now();
        Window current = windows.get(key);
        if (current == null || !current.startedAt.plus(window).isAfter(now)) {
            windows.put(key, new Window(now, 1));
            return;
        }
        if (current.count >= limit) {
            throw new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
                    "Random word suggestion rate limit exceeded. Please wait before trying again.");
        }
        windows.put(key, new Window(current.startedAt, current.count + 1));
    }

    private record Window(Instant startedAt, int count) {
    }
}
