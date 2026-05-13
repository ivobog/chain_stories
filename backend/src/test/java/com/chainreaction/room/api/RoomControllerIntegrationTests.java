package com.chainreaction.room.api;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.chainreaction.auth.api.AuthResponse;
import com.chainreaction.subscription.domain.Subscription;
import com.chainreaction.subscription.domain.SubscriptionPlan;
import com.chainreaction.subscription.repository.SubscriptionRepository;
import com.chainreaction.user.domain.User;
import com.chainreaction.user.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class RoomControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Test
    void freeUserCanCreateTwoPlayerRoomAndSecondUserCanJoin() throws Exception {
        AuthResponse host = register("host-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-" + UUID.randomUUID() + "@example.com", "Player");

        MvcResult createResult = createRoom(host.accessToken(), 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId", not(blankOrNullString())))
                .andExpect(jsonPath("$.roomCode", not(blankOrNullString())))
                .andExpect(jsonPath("$.status", equalTo("LOBBY")))
                .andExpect(jsonPath("$.settings.maxPlayers", equalTo(2)))
                .andExpect(jsonPath("$.participants[0].displayName", equalTo("Host")))
                .andExpect(jsonPath("$.participants[0].role", equalTo("HOST")))
                .andReturn();

        Map<String, Object> room = responseBody(createResult);
        String roomCode = (String) room.get("roomCode");
        String roomId = (String) room.get("roomId");

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()", equalTo(2)))
                .andExpect(jsonPath("$.participants[1].displayName", equalTo("Player")))
                .andExpect(jsonPath("$.participants[1].role", equalTo("PLAYER")));

        mockMvc.perform(get("/api/v1/rooms/" + roomId)
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode", equalTo(roomCode)));
    }

    @Test
    void freeUserCannotCreateRoomAbovePlanLimitButPaidUserCan() throws Exception {
        AuthResponse freeUser = register("free-room-" + UUID.randomUUID() + "@example.com", "Free");

        createRoom(freeUser.accessToken(), 3)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", equalTo("ENTITLEMENT_REQUIRED")));

        AuthResponse paidUser = register("paid-room-" + UUID.randomUUID() + "@example.com", "Paid");
        setPlan(paidUser.userId(), SubscriptionPlan.PLUS);

        createRoom(paidUser.accessToken(), 8)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.maxPlayers", equalTo(8)));
    }

    @Test
    void joinRejectsFullAndClosedRooms() throws Exception {
        AuthResponse host = register("host-full-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-full-" + UUID.randomUUID() + "@example.com", "Player");
        AuthResponse extra = register("extra-full-" + UUID.randomUUID() + "@example.com", "Extra");

        MvcResult createResult = createRoom(host.accessToken(), 2).andExpect(status().isOk()).andReturn();
        Map<String, Object> room = responseBody(createResult);
        String roomCode = (String) room.get("roomCode");
        String roomId = (String) room.get("roomId");

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + extra.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("ROOM_FULL")));

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/close")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("CLOSED")));

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + extra.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("ROOM_CLOSED")));
    }

    @Test
    void roomPreviewShowsJoinAvailabilityWithoutParticipantEmails() throws Exception {
        AuthResponse host = register("host-preview-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-preview-" + UUID.randomUUID() + "@example.com", "Player");
        AuthResponse extra = register("extra-preview-" + UUID.randomUUID() + "@example.com", "Extra");

        MvcResult createResult = createRoom(host.accessToken(), 2).andExpect(status().isOk()).andReturn();
        Map<String, Object> room = responseBody(createResult);
        String roomCode = (String) room.get("roomCode");

        mockMvc.perform(get("/api/v1/rooms/code/" + roomCode.toLowerCase() + "/preview")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode", equalTo(roomCode)))
                .andExpect(jsonPath("$.status", equalTo("LOBBY")))
                .andExpect(jsonPath("$.hostDisplayName", equalTo("Host")))
                .andExpect(jsonPath("$.settings.maxPlayers", equalTo(2)))
                .andExpect(jsonPath("$.activePlayers", equalTo(1)))
                .andExpect(jsonPath("$.alreadyJoined", equalTo(false)))
                .andExpect(jsonPath("$.canJoin", equalTo(true)));

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/rooms/code/" + roomCode + "/preview")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activePlayers", equalTo(2)))
                .andExpect(jsonPath("$.alreadyJoined", equalTo(true)))
                .andExpect(jsonPath("$.canJoin", equalTo(true)));

        mockMvc.perform(get("/api/v1/rooms/code/" + roomCode + "/preview")
                        .header("Authorization", "Bearer " + extra.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activePlayers", equalTo(2)))
                .andExpect(jsonPath("$.alreadyJoined", equalTo(false)))
                .andExpect(jsonPath("$.canJoin", equalTo(false)));
    }

    @Test
    void myRoomsListsOnlyActiveJoinedRooms() throws Exception {
        AuthResponse host = register("host-list-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-list-" + UUID.randomUUID() + "@example.com", "Player");

        MvcResult openCreateResult = createRoom(host.accessToken(), 2).andExpect(status().isOk()).andReturn();
        Map<String, Object> openRoom = responseBody(openCreateResult);
        String openRoomCode = (String) openRoom.get("roomCode");
        String openRoomId = (String) openRoom.get("roomId");

        MvcResult closedCreateResult = createRoom(host.accessToken(), 2).andExpect(status().isOk()).andReturn();
        Map<String, Object> closedRoom = responseBody(closedCreateResult);
        String closedRoomCode = (String) closedRoom.get("roomCode");
        String closedRoomId = (String) closedRoom.get("roomId");

        mockMvc.perform(post("/api/v1/rooms/" + openRoomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/rooms/" + closedRoomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/rooms/" + closedRoomId + "/close")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/rooms")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", equalTo(1)))
                .andExpect(jsonPath("$[0].roomId", equalTo(openRoomId)))
                .andExpect(jsonPath("$[0].roomCode", equalTo(openRoomCode)))
                .andExpect(jsonPath("$[0].hostDisplayName", equalTo("Host")))
                .andExpect(jsonPath("$[0].myRole", equalTo("PLAYER")))
                .andExpect(jsonPath("$[0].activePlayers", equalTo(2)));

        mockMvc.perform(post("/api/v1/rooms/" + openRoomId + "/leave")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/rooms")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", equalTo(0)));
    }

    @Test
    void hostOnlyActionsAreEnforcedAndKickedUserCannotRejoin() throws Exception {
        AuthResponse host = register("host-kick-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-kick-" + UUID.randomUUID() + "@example.com", "Player");

        MvcResult createResult = createRoom(host.accessToken(), 2).andExpect(status().isOk()).andReturn();
        Map<String, Object> room = responseBody(createResult);
        String roomCode = (String) room.get("roomCode");
        String roomId = (String) room.get("roomId");

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/close")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", equalTo("ACCESS_DENIED")));

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/participants/" + player.userId() + "/kick")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[1].status", equalTo("KICKED")));

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", equalTo("ACCESS_DENIED")));
    }

    @Test
    void hostCannotKickInactiveParticipantOrKickAfterRoomClosed() throws Exception {
        AuthResponse host = register("host-kick-state-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-kick-state-" + UUID.randomUUID() + "@example.com", "Player");

        MvcResult createResult = createRoom(host.accessToken(), 2).andExpect(status().isOk()).andReturn();
        Map<String, Object> room = responseBody(createResult);
        String roomCode = (String) room.get("roomCode");
        String roomId = (String) room.get("roomId");

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/leave")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/participants/" + player.userId() + "/kick")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", equalTo("ACCESS_DENIED")));

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/close")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/participants/" + player.userId() + "/kick")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("ROOM_CLOSED")));
    }

    @Test
    void hostCanUpdateLobbySettingsWithinPlanLimit() throws Exception {
        AuthResponse host = register("host-settings-" + UUID.randomUUID() + "@example.com", "Host");
        setPlan(host.userId(), SubscriptionPlan.PLUS);

        MvcResult createResult = createRoom(host.accessToken(), 2).andExpect(status().isOk()).andReturn();
        Map<String, Object> room = responseBody(createResult);
        String roomId = (String) room.get("roomId");

        updateSettings(host.accessToken(), roomId, 8)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.writingStyle", equalTo("HORROR")))
                .andExpect(jsonPath("$.settings.language", equalTo("de-ch")))
                .andExpect(jsonPath("$.settings.safetyMode", equalTo("FAMILY")))
                .andExpect(jsonPath("$.settings.maxPlayers", equalTo(8)))
                .andExpect(jsonPath("$.settings.turnLimit", equalTo(12)))
                .andExpect(jsonPath("$.settings.turnTimeoutSeconds", equalTo(45)))
                .andExpect(jsonPath("$.settings.visibility", equalTo("PUBLIC")));
    }

    @Test
    void updateSettingsRejectsNonHostPlanOverageAndInvalidRoomLimit() throws Exception {
        AuthResponse host = register("host-settings-reject-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-settings-reject-" + UUID.randomUUID() + "@example.com", "Player");

        MvcResult createResult = createRoom(host.accessToken(), 2).andExpect(status().isOk()).andReturn();
        Map<String, Object> room = responseBody(createResult);
        String roomCode = (String) room.get("roomCode");
        String roomId = (String) room.get("roomId");

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        updateSettings(player.accessToken(), roomId, 2)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", equalTo("ACCESS_DENIED")));

        updateSettings(host.accessToken(), roomId, 3)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", equalTo("ENTITLEMENT_REQUIRED")));

        setPlan(host.userId(), SubscriptionPlan.PLUS);
        updateSettings(host.accessToken(), roomId, 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", equalTo("VALIDATION_FAILED")));
    }

    @Test
    void updateSettingsCannotShrinkBelowActiveParticipants() throws Exception {
        AuthResponse host = register("h-cap-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse playerOne = register("p1-cap-" + UUID.randomUUID() + "@example.com", "One");
        AuthResponse playerTwo = register("p2-cap-" + UUID.randomUUID() + "@example.com", "Two");
        setPlan(host.userId(), SubscriptionPlan.PLUS);

        MvcResult createResult = createRoom(host.accessToken(), 3).andExpect(status().isOk()).andReturn();
        Map<String, Object> room = responseBody(createResult);
        String roomCode = (String) room.get("roomCode");
        String roomId = (String) room.get("roomId");

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + playerOne.accessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + playerTwo.accessToken()))
                .andExpect(status().isOk());

        updateSettings(host.accessToken(), roomId, 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("ROOM_FULL")));
    }

    @Test
    void updateSettingsRejectsClosedRoom() throws Exception {
        AuthResponse host = register("host-settings-closed-" + UUID.randomUUID() + "@example.com", "Host");

        MvcResult createResult = createRoom(host.accessToken(), 2).andExpect(status().isOk()).andReturn();
        Map<String, Object> room = responseBody(createResult);
        String roomId = (String) room.get("roomId");

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/close")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk());

        updateSettings(host.accessToken(), roomId, 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("ROOM_CLOSED")));
    }

    @Test
    void closeAndLeaveRejectClosedRooms() throws Exception {
        AuthResponse host = register("host-close-state-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-close-state-" + UUID.randomUUID() + "@example.com", "Player");

        MvcResult createResult = createRoom(host.accessToken(), 2).andExpect(status().isOk()).andReturn();
        Map<String, Object> room = responseBody(createResult);
        String roomCode = (String) room.get("roomCode");
        String roomId = (String) room.get("roomId");

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/close")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/close")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("ROOM_CLOSED")));

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/leave")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("ROOM_CLOSED")));
    }

    @Test
    void playerCanLeaveAndRejoinRoom() throws Exception {
        AuthResponse host = register("host-rejoin-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-rejoin-" + UUID.randomUUID() + "@example.com", "Player");

        MvcResult createResult = createRoom(host.accessToken(), 2).andExpect(status().isOk()).andReturn();
        Map<String, Object> room = responseBody(createResult);
        String roomCode = (String) room.get("roomCode");
        String roomId = (String) room.get("roomId");

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/leave")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[1].role", equalTo("PLAYER")))
                .andExpect(jsonPath("$.participants[1].status", equalTo("LEFT")));

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[1].role", equalTo("PLAYER")))
                .andExpect(jsonPath("$.participants[1].status", equalTo("JOINED")));
    }

    @Test
    void rejoinStillRequiresAvailableRoomSlot() throws Exception {
        AuthResponse host = register("host-rejoin-full-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-rejoin-full-" + UUID.randomUUID() + "@example.com", "Player");
        AuthResponse extra = register("extra-rejoin-full-" + UUID.randomUUID() + "@example.com", "Extra");

        MvcResult createResult = createRoom(host.accessToken(), 2).andExpect(status().isOk()).andReturn();
        Map<String, Object> room = responseBody(createResult);
        String roomCode = (String) room.get("roomCode");
        String roomId = (String) room.get("roomId");

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/leave")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + extra.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("ROOM_FULL")));
    }

    @Test
    void hostLeaveTransfersHostToEarliestJoinedPlayer() throws Exception {
        AuthResponse host = register("host-transfer-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-transfer-" + UUID.randomUUID() + "@example.com", "Player");

        MvcResult createResult = createRoom(host.accessToken(), 2).andExpect(status().isOk()).andReturn();
        Map<String, Object> room = responseBody(createResult);
        String roomCode = (String) room.get("roomCode");
        String roomId = (String) room.get("roomId");

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/leave")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hostUserId", equalTo(player.userId().toString())))
                .andExpect(jsonPath("$.participants[0].role", equalTo("PLAYER")))
                .andExpect(jsonPath("$.participants[0].status", equalTo("LEFT")))
                .andExpect(jsonPath("$.participants[1].role", equalTo("HOST")))
                .andExpect(jsonPath("$.participants[1].status", equalTo("JOINED")));

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hostUserId", equalTo(player.userId().toString())))
                .andExpect(jsonPath("$.participants[0].role", equalTo("PLAYER")))
                .andExpect(jsonPath("$.participants[0].status", equalTo("JOINED")))
                .andExpect(jsonPath("$.participants[1].role", equalTo("HOST")));
    }

    @Test
    void lastHostLeavingClosesRoom() throws Exception {
        AuthResponse host = register("host-close-leave-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse latePlayer = register("late-close-leave-" + UUID.randomUUID() + "@example.com", "Late");

        MvcResult createResult = createRoom(host.accessToken(), 2).andExpect(status().isOk()).andReturn();
        Map<String, Object> room = responseBody(createResult);
        String roomCode = (String) room.get("roomCode");
        String roomId = (String) room.get("roomId");

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/leave")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("CLOSED")))
                .andExpect(jsonPath("$.participants[0].role", equalTo("PLAYER")))
                .andExpect(jsonPath("$.participants[0].status", equalTo("LEFT")));

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + latePlayer.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("ROOM_CLOSED")));
    }

    private ResultActions createRoom(String accessToken, int maxPlayers) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "writingStyle", "FUNNY",
                        "language", "en",
                        "safetyMode", "TEEN",
                        "maxPlayers", maxPlayers,
                        "turnLimit", 10,
                        "turnTimeoutSeconds", 30,
                        "visibility", "PRIVATE"))));
    }

    private ResultActions updateSettings(String accessToken, String roomId, int maxPlayers) throws Exception {
        return mockMvc.perform(patch("/api/v1/rooms/" + roomId + "/settings")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "writingStyle", "HORROR",
                        "language", "DE-CH",
                        "safetyMode", "FAMILY",
                        "maxPlayers", maxPlayers,
                        "turnLimit", 12,
                        "turnTimeoutSeconds", 45,
                        "visibility", "PUBLIC"))));
    }

    private AuthResponse register(String email, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", "SecretPassword123!",
                                "displayName", displayName))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), AuthResponse.class);
    }

    private void setPlan(UUID userId, SubscriptionPlan plan) {
        User user = userRepository.findById(userId).orElseThrow();
        subscriptionRepository.save(new Subscription(user, plan));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private Map<String, Object> responseBody(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsByteArray(),
                new TypeReference<>() {
                });
    }
}
