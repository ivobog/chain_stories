package com.chainreaction.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class WebSocketConnectionMetricsTests {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final WebSocketConnectionMetrics metrics = new WebSocketConnectionMetrics(meterRegistry);

    @Test
    void tracksActiveUniqueWebSocketSessions() {
        metrics.handleConnected(connected("session-1"));
        metrics.handleConnected(connected("session-1"));
        metrics.handleConnected(connected("session-2"));

        assertThat(activeConnections()).isEqualTo(2);

        metrics.handleDisconnected(disconnected("session-1"));

        assertThat(activeConnections()).isEqualTo(1);

        metrics.handleDisconnected(disconnected("missing-session"));

        assertThat(activeConnections()).isEqualTo(1);
    }

    private SessionConnectedEvent connected(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
        accessor.setSessionId(sessionId);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionConnectedEvent(this, message, null);
    }

    private SessionDisconnectEvent disconnected(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(sessionId);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionDisconnectEvent(this, message, sessionId, CloseStatus.NORMAL);
    }

    private double activeConnections() {
        return meterRegistry.get("websocket_connections_active").gauge().value();
    }
}
