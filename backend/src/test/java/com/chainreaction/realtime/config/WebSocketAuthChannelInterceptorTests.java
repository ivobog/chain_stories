package com.chainreaction.realtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.chainreaction.common.security.CurrentUserPrincipal;
import com.chainreaction.common.security.JwtClaims;
import com.chainreaction.common.security.JwtTokenService;
import com.chainreaction.room.domain.RoomParticipant;
import com.chainreaction.room.domain.RoomParticipantStatus;
import com.chainreaction.room.repository.RoomParticipantRepository;
import com.chainreaction.user.domain.User;
import com.chainreaction.user.repository.UserRepository;

class WebSocketAuthChannelInterceptorTests {

    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoomParticipantRepository roomParticipantRepository = mock(RoomParticipantRepository.class);
    private final MessageChannel channel = mock(MessageChannel.class);
    private final WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(
            jwtTokenService,
            userRepository,
            roomParticipantRepository);

    @Test
    void connectRequiresBearerToken() {
        Message<byte[]> message = connectMessage(null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("bearer token");
    }

    @Test
    void connectAuthenticatesActiveJwtUser() {
        User user = new User("ws-" + UUID.randomUUID() + "@example.com", "hash");
        when(jwtTokenService.validate("token")).thenReturn(new JwtClaims(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                Instant.now().plusSeconds(60)));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        Message<?> result = interceptor.preSend(connectMessage("Bearer token"), channel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser()).isNotNull();
        Authentication authentication = (Authentication) accessor.getUser();
        assertThat(authentication.getPrincipal()).isInstanceOf(CurrentUserPrincipal.class);
        CurrentUserPrincipal principal = (CurrentUserPrincipal) authentication.getPrincipal();
        assertThat(principal.getUserId()).isEqualTo(user.getId());
    }

    @Test
    void nonConnectFramesPassThrough() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void subscribeAllowsActiveRoomParticipant() {
        User user = new User("ws-sub-" + UUID.randomUUID() + "@example.com", "hash");
        UUID roomId = UUID.randomUUID();
        RoomParticipant participant = mock(RoomParticipant.class);
        when(participant.getStatus()).thenReturn(RoomParticipantStatus.JOINED);
        when(roomParticipantRepository.findByRoomIdAndUserId(roomId, user.getId()))
                .thenReturn(Optional.of(participant));

        Message<byte[]> message = subscribeMessage("/topic/rooms/" + roomId, user);

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void subscribeRejectsNonParticipants() {
        User user = new User("ws-outsider-" + UUID.randomUUID() + "@example.com", "hash");
        UUID roomId = UUID.randomUUID();
        when(roomParticipantRepository.findByRoomIdAndUserId(roomId, user.getId()))
                .thenReturn(Optional.empty());

        Message<byte[]> message = subscribeMessage("/topic/rooms/" + roomId, user);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("active room participants");
    }

    @Test
    void subscribeRejectsInvalidRoomTopic() {
        User user = new User("ws-invalid-topic-" + UUID.randomUUID() + "@example.com", "hash");
        Message<byte[]> message = subscribeMessage("/topic/rooms/not-a-room-id", user);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void subscribeAllowsAuthenticatedUserQueue() {
        User user = new User("ws-user-queue-" + UUID.randomUUID() + "@example.com", "hash");
        Message<byte[]> message = subscribeMessage("/user/queue/events", user);

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void subscribeRejectsUnauthenticatedUserQueue() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/queue/events");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("requires authentication");
    }

    private Message<byte[]> connectMessage(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeMessage(String destination, User user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        CurrentUserPrincipal principal = new CurrentUserPrincipal(user);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
