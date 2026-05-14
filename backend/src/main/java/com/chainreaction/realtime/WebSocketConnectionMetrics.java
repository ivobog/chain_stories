package com.chainreaction.realtime;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class WebSocketConnectionMetrics {

    private final Set<String> activeSessionIds = ConcurrentHashMap.newKeySet();
    private final AtomicInteger activeConnections = new AtomicInteger();

    public WebSocketConnectionMetrics(MeterRegistry meterRegistry) {
        Gauge.builder("websocket_connections_active", activeConnections, AtomicInteger::get)
                .description("Currently active authenticated WebSocket/STOMP connections.")
                .register(meterRegistry);
    }

    @EventListener
    public void handleConnected(SessionConnectedEvent event) {
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (sessionId != null && activeSessionIds.add(sessionId)) {
            activeConnections.incrementAndGet();
        }
    }

    @EventListener
    public void handleDisconnected(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId != null && activeSessionIds.remove(sessionId)) {
            activeConnections.decrementAndGet();
        }
    }
}
