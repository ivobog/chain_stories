package com.chainreaction.realtime.config;

import java.util.List;
import java.util.UUID;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.security.CurrentUserPrincipal;
import com.chainreaction.common.security.JwtClaims;
import com.chainreaction.common.security.JwtTokenService;
import com.chainreaction.room.domain.RoomParticipantStatus;
import com.chainreaction.room.repository.RoomParticipantRepository;
import com.chainreaction.user.domain.User;
import com.chainreaction.user.repository.UserRepository;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROOM_TOPIC_PREFIX = "/topic/rooms/";
    private static final String USER_QUEUE_DESTINATION = "/user/queue/events";
    private static final String SESSION_PRINCIPAL_ATTRIBUTE = "chainReactionPrincipal";

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;
    private final RoomParticipantRepository roomParticipantRepository;

    public WebSocketAuthChannelInterceptor(
            JwtTokenService jwtTokenService,
            UserRepository userRepository,
            RoomParticipantRepository roomParticipantRepository) {
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
        this.roomParticipantRepository = roomParticipantRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            accessor = StompHeaderAccessor.wrap(message);
        }
        if (accessor.getCommand() != StompCommand.CONNECT) {
            if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
                authorizeSubscription(accessor);
            }
            return message;
        }

        String authorization = authorizationHeader(accessor);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("WebSocket connection requires a bearer token.");
        }

        try {
            JwtClaims claims = jwtTokenService.validate(authorization.substring(BEARER_PREFIX.length()));
            User user = userRepository.findById(claims.userId())
                    .filter(User::isActive)
                    .orElseThrow(() -> new AccessDeniedException("WebSocket user is not active."));
            CurrentUserPrincipal principal = new CurrentUserPrincipal(user);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.getAuthorities());
            accessor.setUser(authentication);
            if (accessor.getSessionAttributes() != null) {
                accessor.getSessionAttributes().put(SESSION_PRINCIPAL_ATTRIBUTE, principal);
            }
            return message;
        } catch (ApiException exception) {
            throw new AccessDeniedException("WebSocket bearer token is invalid.", exception);
        }
    }

    private String authorizationHeader(StompHeaderAccessor accessor) {
        List<String> values = accessor.getNativeHeader("Authorization");
        if (values == null || values.isEmpty()) {
            values = accessor.getNativeHeader("authorization");
        }
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }
        if (destination.equals(USER_QUEUE_DESTINATION)) {
            currentPrincipal(accessor);
            return;
        }
        if (!destination.startsWith(ROOM_TOPIC_PREFIX)) {
            return;
        }

        CurrentUserPrincipal principal = currentPrincipal(accessor);
        UUID roomId = roomId(destination);
        boolean activeParticipant = roomParticipantRepository.findByRoomIdAndUserId(roomId, principal.getUserId())
                .filter(participant -> participant.getStatus() == RoomParticipantStatus.JOINED)
                .isPresent();
        if (!activeParticipant) {
            throw new AccessDeniedException("Only active room participants can subscribe to this room topic.");
        }
    }

    private CurrentUserPrincipal currentPrincipal(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof CurrentUserPrincipal principal) {
            return principal;
        }
        if (accessor.getSessionAttributes() != null
                && accessor.getSessionAttributes().get(SESSION_PRINCIPAL_ATTRIBUTE) instanceof CurrentUserPrincipal principal) {
            return principal;
        }
        throw new AccessDeniedException("WebSocket subscription requires authentication.");
    }

    private UUID roomId(String destination) {
        String rawRoomId = destination.substring(ROOM_TOPIC_PREFIX.length());
        try {
            return UUID.fromString(rawRoomId);
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Room topic destination is invalid.", exception);
        }
    }
}
