package com.chainreaction.room.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;
import com.chainreaction.observability.ApplicationMetrics;
import com.chainreaction.observability.ApplicationObservations;
import com.chainreaction.realtime.api.RealtimeEventType;
import com.chainreaction.realtime.service.RealtimeEventPublisher;
import com.chainreaction.room.api.CreateRoomRequest;
import com.chainreaction.room.api.RoomPreviewResponse;
import com.chainreaction.room.api.RoomResponse;
import com.chainreaction.room.api.RoomSettingsResponse;
import com.chainreaction.room.api.RoomSummaryResponse;
import com.chainreaction.room.api.UpdateRoomSettingsRequest;
import com.chainreaction.room.domain.Room;
import com.chainreaction.room.domain.RoomParticipant;
import com.chainreaction.room.domain.RoomParticipantRole;
import com.chainreaction.room.domain.RoomParticipantStatus;
import com.chainreaction.room.domain.RoomStatus;
import com.chainreaction.room.repository.RoomParticipantRepository;
import com.chainreaction.room.repository.RoomRepository;
import com.chainreaction.subscription.service.EntitlementService;
import com.chainreaction.user.domain.User;
import com.chainreaction.user.repository.UserProfileRepository;
import com.chainreaction.user.repository.UserRepository;

import io.micrometer.common.KeyValue;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final EntitlementService entitlementService;
    private final RoomCodeGenerator roomCodeGenerator;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final ApplicationMetrics applicationMetrics;
    private final ApplicationObservations applicationObservations;

    public RoomService(
            RoomRepository roomRepository,
            RoomParticipantRepository roomParticipantRepository,
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            EntitlementService entitlementService,
            RoomCodeGenerator roomCodeGenerator,
            RealtimeEventPublisher realtimeEventPublisher,
            ApplicationMetrics applicationMetrics,
            ApplicationObservations applicationObservations) {
        this.roomRepository = roomRepository;
        this.roomParticipantRepository = roomParticipantRepository;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.entitlementService = entitlementService;
        this.roomCodeGenerator = roomCodeGenerator;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.applicationMetrics = applicationMetrics;
        this.applicationObservations = applicationObservations;
    }

    @Transactional
    public RoomResponse createRoom(UUID userId, CreateRoomRequest request) {
        return applicationObservations.observe(
                "room.create",
                () -> createRoomObserved(userId, request),
                KeyValue.of("writing_style", request.writingStyle().name().toLowerCase()),
                KeyValue.of("language", request.language().toLowerCase()),
                KeyValue.of("safety_mode", request.safetyMode().name().toLowerCase()));
    }

    private RoomResponse createRoomObserved(UUID userId, CreateRoomRequest request) {
        User host = requireActiveUser(userId);
        assertWithinPlanLimit(userId, request.maxPlayers());

        String roomCode = generateUniqueRoomCode();
        Room room = roomRepository.save(new Room(
                roomCode,
                host,
                request.writingStyle(),
                request.language().toLowerCase(),
                request.safetyMode(),
                request.maxPlayers(),
                request.turnLimit(),
                request.turnTimeoutSeconds(),
                request.visibility()));
        roomParticipantRepository.save(new RoomParticipant(room, host, RoomParticipantRole.HOST));
        applicationMetrics.recordRoomCreated(room.getWritingStyle(), room.getLanguage(), room.getSafetyMode());
        return response(room);
    }

    @Transactional(readOnly = true)
    public List<RoomSummaryResponse> listMyRooms(UUID userId) {
        requireActiveUser(userId);
        return roomParticipantRepository.findActiveRoomsForUser(
                        userId,
                        RoomParticipantStatus.JOINED,
                        EnumSet.of(RoomStatus.LOBBY, RoomStatus.ACTIVE))
                .stream()
                .map(this::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomPreviewResponse previewRoom(UUID userId, String roomCode) {
        requireActiveUser(userId);
        Room room = roomRepository.findByRoomCodeIgnoreCase(roomCode)
                .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Room does not exist."));
        long activePlayers = activePlayerCount(room);
        RoomParticipantStatus existingStatus = roomParticipantRepository.findByRoomIdAndUserId(room.getId(), userId)
                .map(RoomParticipant::getStatus)
                .orElse(null);
        boolean alreadyJoined = existingStatus == RoomParticipantStatus.JOINED;
        boolean blocked = existingStatus == RoomParticipantStatus.KICKED;
        boolean open = isJoinable(room);
        boolean canJoin = open && !blocked && (alreadyJoined || activePlayers < room.getMaxPlayers());
        return new RoomPreviewResponse(
                room.getRoomCode(),
                room.getStatus(),
                displayName(room.getHost()),
                settings(room),
                activePlayers,
                alreadyJoined,
                canJoin);
    }

    @Transactional
    public RoomResponse joinRoom(UUID userId, String roomCode) {
        return applicationObservations.observe(
                "room.join",
                () -> joinRoomObserved(userId, roomCode));
    }

    private RoomResponse joinRoomObserved(UUID userId, String roomCode) {
        User user = requireActiveUser(userId);
        Room room = roomRepository.findByRoomCodeIgnoreCase(roomCode)
                .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Room does not exist."));
        assertLobbyJoinable(room);

        RoomResponse result = roomParticipantRepository.findByRoomIdAndUserId(room.getId(), userId)
                .map(existing -> {
                    if (existing.getStatus() == RoomParticipantStatus.KICKED) {
                        throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                                "This user was removed from the room.");
                    }
                    if (existing.getStatus() != RoomParticipantStatus.JOINED) {
                        assertRoomHasCapacity(room);
                    }
                    existing.rejoin();
                    return response(room);
                })
                .orElseGet(() -> {
                    assertRoomHasCapacity(room);
                    roomParticipantRepository.save(new RoomParticipant(room, user, RoomParticipantRole.PLAYER));
                    return response(room);
                });
        realtimeEventPublisher.publishRoomEvent(room.getId(), RealtimeEventType.PLAYER_JOINED, result);
        return result;
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoom(UUID userId, UUID roomId) {
        Room room = requireRoom(roomId);
        requireParticipant(roomId, userId);
        return response(room);
    }

    @Transactional
    public RoomResponse closeRoom(UUID userId, UUID roomId) {
        Room room = requireRoom(roomId);
        requireHost(room, userId);
        assertJoinable(room);
        room.close();
        RoomResponse result = response(room);
        realtimeEventPublisher.publishRoomEvent(roomId, RealtimeEventType.ROOM_CLOSED, result);
        return result;
    }

    @Transactional
    public RoomResponse updateSettings(UUID userId, UUID roomId, UpdateRoomSettingsRequest request) {
        Room room = requireRoom(roomId);
        requireHost(room, userId);
        assertLobbyEditable(room);
        assertWithinPlanLimit(userId, request.maxPlayers());
        assertMaxPlayersCanFitActiveParticipants(room, request.maxPlayers());

        room.updateSettings(
                request.writingStyle(),
                request.language().toLowerCase(),
                request.safetyMode(),
                request.maxPlayers(),
                request.turnLimit(),
                request.turnTimeoutSeconds(),
                request.visibility());
        return response(room);
    }

    @Transactional
    public RoomResponse kickParticipant(UUID hostUserId, UUID roomId, UUID targetUserId) {
        Room room = requireRoom(roomId);
        requireHost(room, hostUserId);
        assertJoinable(room);
        if (room.getHost().getId().equals(targetUserId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Host cannot be kicked.");
        }
        RoomParticipant participant = roomParticipantRepository.findByRoomIdAndUserId(roomId, targetUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.NOT_FOUND,
                        "Participant is not in the room."));
        if (participant.getStatus() != RoomParticipantStatus.JOINED) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                    "Participant is not active in the room.");
        }
        participant.kick();
        RoomResponse result = response(room);
        realtimeEventPublisher.publishRoomEvent(roomId, RealtimeEventType.PLAYER_KICKED, result);
        realtimeEventPublisher.publishUserEvent(participant.getUser().getEmail(), RealtimeEventType.PLAYER_KICKED, result);
        return result;
    }

    @Transactional
    public RoomResponse leaveRoom(UUID userId, UUID roomId) {
        Room room = requireRoom(roomId);
        assertJoinable(room);
        RoomParticipant participant = roomParticipantRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                        "User is not a room participant."));
        if (participant.getStatus() != RoomParticipantStatus.JOINED) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                    "User is not an active room participant.");
        }

        if (participant.getRole() == RoomParticipantRole.HOST) {
            transferOrCloseRoom(room, participant);
        }
        participant.leave();
        RoomResponse result = response(room);
        realtimeEventPublisher.publishRoomEvent(roomId, RealtimeEventType.PLAYER_LEFT, result);
        if (room.getStatus() == RoomStatus.CLOSED) {
            realtimeEventPublisher.publishRoomEvent(roomId, RealtimeEventType.ROOM_CLOSED, result);
        }
        return result;
    }

    private void transferOrCloseRoom(Room room, RoomParticipant departingHost) {
        roomParticipantRepository.findAllByRoomIdOrderByJoinedAtAsc(room.getId()).stream()
                .filter(candidate -> candidate.getStatus() == RoomParticipantStatus.JOINED)
                .filter(candidate -> !candidate.getUser().getId().equals(departingHost.getUser().getId()))
                .findFirst()
                .ifPresentOrElse(nextHost -> {
                    departingHost.demoteToPlayer();
                    nextHost.promoteToHost();
                    room.transferHost(nextHost.getUser());
                }, () -> {
                    departingHost.demoteToPlayer();
                    room.close();
                });
    }

    private void assertJoinable(Room room) {
        if (!isJoinable(room)) {
            throw new ApiException(ErrorCode.ROOM_CLOSED, HttpStatus.CONFLICT, "Room is no longer joinable.");
        }
    }

    private void assertLobbyJoinable(Room room) {
        if (!isJoinable(room) || room.getStatus() != RoomStatus.LOBBY) {
            throw new ApiException(ErrorCode.ROOM_CLOSED, HttpStatus.CONFLICT, "Room is no longer joinable.");
        }
    }

    private boolean isJoinable(Room room) {
        return room.getStatus() != RoomStatus.CLOSED
                && room.getStatus() != RoomStatus.EXPIRED
                && room.getStatus() != RoomStatus.BANNED;
    }

    private void assertLobbyEditable(Room room) {
        if (room.getStatus() != RoomStatus.LOBBY) {
            throw new ApiException(ErrorCode.ROOM_CLOSED, HttpStatus.CONFLICT,
                    "Room settings can no longer be changed.");
        }
    }

    private void assertWithinPlanLimit(UUID userId, int maxPlayers) {
        int planMaxPlayers = entitlementService.features(userId).maxPlayersPerRoom();
        if (maxPlayers > planMaxPlayers) {
            throw new ApiException(
                    ErrorCode.ENTITLEMENT_REQUIRED,
                    HttpStatus.FORBIDDEN,
                    "Requested room size exceeds the user's plan limit.");
        }
    }

    private void assertMaxPlayersCanFitActiveParticipants(Room room, int maxPlayers) {
        long joinedPlayers = activePlayerCount(room);
        if (joinedPlayers > maxPlayers) {
            throw new ApiException(ErrorCode.ROOM_FULL, HttpStatus.CONFLICT,
                    "Room already has more active players than the requested limit.");
        }
    }

    private void assertRoomHasCapacity(Room room) {
        long joinedPlayers = activePlayerCount(room);
        if (joinedPlayers >= room.getMaxPlayers()) {
            throw new ApiException(ErrorCode.ROOM_FULL, HttpStatus.CONFLICT,
                    "Room has reached its player limit.");
        }
    }

    private void requireHost(Room room, UUID userId) {
        if (!room.getHost().getId().equals(userId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Only the host can do this.");
        }
    }

    private void requireParticipant(UUID roomId, UUID userId) {
        RoomParticipant participant = roomParticipantRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                        "User is not a room participant."));
        if (participant.getStatus() != RoomParticipantStatus.JOINED) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                    "User is not an active room participant.");
        }
    }

    private User requireActiveUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED,
                        "Authenticated user no longer exists."));
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "User account is not active.");
        }
        return user;
    }

    private Room requireRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Room does not exist."));
    }

    private RoomResponse response(Room room) {
        List<RoomParticipant> participants = roomParticipantRepository.findAllByRoomIdOrderByJoinedAtAsc(room.getId());
        Map<UUID, String> displayNames = participants.stream()
                .map(RoomParticipant::getUser)
                .map(User::getId)
                .distinct()
                .collect(Collectors.toMap(
                        Function.identity(),
                        userId -> userProfileRepository.findByUserId(userId)
                                .map(profile -> profile.getDisplayName())
                                .orElse("Unknown user")));
        return RoomResponse.from(room, participants, displayNames);
    }

    private RoomSummaryResponse summary(RoomParticipant participant) {
        Room room = participant.getRoom();
        return new RoomSummaryResponse(
                room.getId(),
                room.getRoomCode(),
                room.getStatus(),
                room.getHost().getId(),
                displayName(room.getHost()),
                participant.getRole(),
                settings(room),
                activePlayerCount(room));
    }

    private RoomSettingsResponse settings(Room room) {
        return new RoomSettingsResponse(
                room.getWritingStyle(),
                room.getLanguage(),
                room.getSafetyMode(),
                room.getMaxPlayers(),
                room.getTurnLimit(),
                room.getTurnTimeoutSeconds(),
                room.getVisibility());
    }

    private String displayName(User user) {
        return userProfileRepository.findByUserId(user.getId())
                .map(profile -> profile.getDisplayName())
                .orElse("Unknown user");
    }

    private long activePlayerCount(Room room) {
        return roomParticipantRepository.countByRoomIdAndStatus(room.getId(), RoomParticipantStatus.JOINED);
    }

    private String generateUniqueRoomCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = roomCodeGenerator.generate();
            if (!roomRepository.existsByRoomCodeIgnoreCase(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique room code.");
    }
}
