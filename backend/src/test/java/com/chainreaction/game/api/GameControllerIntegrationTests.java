package com.chainreaction.game.api;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.chainreaction.ai.AiGenerationAttemptStatus;
import com.chainreaction.ai.AiGenerationAttemptRepository;
import com.chainreaction.auth.api.AuthResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class GameControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiGenerationAttemptRepository aiGenerationAttemptRepository;

    @Test
    void hostCanStartGameAndPlayersCanFetchState() throws Exception {
        AuthResponse host = register("host-game-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-game-" + UUID.randomUUID() + "@example.com", "Player");

        MvcResult createResult = createRoom(host.accessToken()).andExpect(status().isOk()).andReturn();
        Map<String, Object> room = responseBody(createResult);
        String roomCode = (String) room.get("roomCode");
        String roomId = (String) room.get("roomId");

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        MvcResult startResult = mockMvc.perform(post("/api/v1/rooms/" + roomId + "/games/start")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId", not(blankOrNullString())))
                .andExpect(jsonPath("$.roomId", equalTo(roomId)))
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.currentTurnNumber", equalTo(1)))
                .andExpect(jsonPath("$.currentTurn.turnNumber", equalTo(1)))
                .andExpect(jsonPath("$.currentTurn.playerUserId", equalTo(host.userId().toString())))
                .andExpect(jsonPath("$.turnLimit", equalTo(10)))
                .andExpect(jsonPath("$.turnTimeoutSeconds", equalTo(30)))
                .andExpect(jsonPath("$.startedAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.completedAt", nullValue()))
                .andExpect(jsonPath("$.turnOrder[0]", equalTo(host.userId().toString())))
                .andExpect(jsonPath("$.turnOrder[1]", equalTo(player.userId().toString())))
                .andExpect(jsonPath("$.turns.length()", equalTo(1)))
                .andExpect(jsonPath("$.turns[0].turnNumber", equalTo(1)))
                .andExpect(jsonPath("$.turns[0].status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.turns[0].submittedAt", nullValue()))
                .andExpect(jsonPath("$.fullStory", equalTo("The story begins, waiting for the first word.")))
                .andExpect(jsonPath("$.storySegments.length()", equalTo(1)))
                .andExpect(jsonPath("$.storySegments[0].sequenceNumber", equalTo(1)))
                .andReturn();

        Map<String, Object> game = responseBody(startResult);
        String gameId = (String) game.get("gameId");

        mockMvc.perform(get("/api/v1/games/" + gameId)
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId", equalTo(gameId)))
                .andExpect(jsonPath("$.fullStory", equalTo("The story begins, waiting for the first word.")))
                .andExpect(jsonPath("$.storySegments[0].content", equalTo("The story begins, waiting for the first word.")));

        mockMvc.perform(get("/api/v1/rooms/" + roomId)
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")));
    }

    @Test
    void startGameRequiresHostAndTwoActivePlayers() throws Exception {
        AuthResponse host = register("host-start-rules-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-start-rules-" + UUID.randomUUID() + "@example.com", "Player");

        MvcResult createResult = createRoom(host.accessToken()).andExpect(status().isOk()).andReturn();
        Map<String, Object> room = responseBody(createResult);
        String roomCode = (String) room.get("roomCode");
        String roomId = (String) room.get("roomId");

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/games/start")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("GAME_NOT_READY")));

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/games/start")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", equalTo("ACCESS_DENIED")));
    }

    @Test
    void activeRoomRejectsSecondStartAndLateJoin() throws Exception {
        AuthResponse host = register("host-active-room-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-active-room-" + UUID.randomUUID() + "@example.com", "Player");
        AuthResponse late = register("late-active-room-" + UUID.randomUUID() + "@example.com", "Late");

        MvcResult createResult = createRoom(host.accessToken()).andExpect(status().isOk()).andReturn();
        Map<String, Object> room = responseBody(createResult);
        String roomCode = (String) room.get("roomCode");
        String roomId = (String) room.get("roomId");

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/games/start")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/games/start")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("GAME_ALREADY_STARTED")));

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + late.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("ROOM_CLOSED")));
    }

    @Test
    void getGameRejectsNonParticipants() throws Exception {
        AuthResponse host = register("host-game-access-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-game-access-" + UUID.randomUUID() + "@example.com", "Player");
        AuthResponse outsider = register("outsider-game-access-" + UUID.randomUUID() + "@example.com", "Outsider");

        MvcResult createResult = createRoom(host.accessToken()).andExpect(status().isOk()).andReturn();
        Map<String, Object> room = responseBody(createResult);
        String roomCode = (String) room.get("roomCode");
        String roomId = (String) room.get("roomId");

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        MvcResult startResult = mockMvc.perform(post("/api/v1/rooms/" + roomId + "/games/start")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        String gameId = (String) responseBody(startResult).get("gameId");

        mockMvc.perform(get("/api/v1/games/" + gameId)
                        .header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", equalTo("ACCESS_DENIED")));
    }

    @Test
    void currentPlayerCanSubmitWordAndAdvanceTurn() throws Exception {
        AuthResponse host = register("host-submit-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-submit-" + UUID.randomUUID() + "@example.com", "Player");

        StartedGame started = startTwoPlayerGame(host, player, 10);

        mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/turns/" + started.turnId() + "/submit-word")
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("word", "Dragon"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.currentTurnNumber", equalTo(2)))
                .andExpect(jsonPath("$.currentTurn.turnNumber", equalTo(2)))
                .andExpect(jsonPath("$.currentTurn.playerUserId", equalTo(player.userId().toString())))
                .andExpect(jsonPath("$.turns.length()", equalTo(2)))
                .andExpect(jsonPath("$.turns[0].turnNumber", equalTo(1)))
                .andExpect(jsonPath("$.turns[0].status", equalTo("SUBMITTED")))
                .andExpect(jsonPath("$.turns[0].submittedAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.turns[1].turnNumber", equalTo(2)))
                .andExpect(jsonPath("$.turns[1].status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.turns[1].submittedAt", nullValue()))
                .andExpect(jsonPath("$.storySegments.length()", equalTo(2)))
                .andExpect(jsonPath("$.storySegments[1].turnNumber", equalTo(1)))
                .andExpect(jsonPath("$.storySegments[1].authorUserId", equalTo(host.userId().toString())))
                .andExpect(jsonPath("$.fullStory", equalTo("The story begins, waiting for the first word.\n\nThe word \"dragon\" pushes the story into a stranger turn.")))
                .andExpect(jsonPath("$.storySegments[1].content", equalTo("The word \"dragon\" pushes the story into a stranger turn.")));

        var attempts = aiGenerationAttemptRepository.findAllByGameIdOrderByCreatedAtAsc(UUID.fromString(started.gameId()));
        org.assertj.core.api.Assertions.assertThat(attempts).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(attempts.get(0).getTurnId()).isEqualTo(UUID.fromString(started.turnId()));
        org.assertj.core.api.Assertions.assertThat(attempts.get(0).getNormalizedWord()).isEqualTo("dragon");
        org.assertj.core.api.Assertions.assertThat(attempts.get(0).getAttemptNumber()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(attempts.get(0).getStatus()).isEqualTo(AiGenerationAttemptStatus.SUCCEEDED);
        org.assertj.core.api.Assertions.assertThat(attempts.get(0).getModel()).isEqualTo("mock-story-v1");
        org.assertj.core.api.Assertions.assertThat(attempts.get(0).getPromptTokens()).isPositive();
        org.assertj.core.api.Assertions.assertThat(attempts.get(0).getCompletionTokens()).isPositive();
    }

    @Test
    void reconnectedParticipantCanFetchFullCurrentStoryStateAfterMissedTurnEvents() throws Exception {
        AuthResponse host = register("host-reconnect-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-reconnect-" + UUID.randomUUID() + "@example.com", "Player");

        StartedGame started = startTwoPlayerGame(host, player, 10);

        MvcResult firstSubmit = mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/turns/" + started.turnId() + "/submit-word")
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("word", "Dragon"))))
                .andExpect(status().isOk())
                .andReturn();
        String secondTurnId = (String) responseBody(firstSubmit, "currentTurn").get("turnId");

        MvcResult secondSubmit = mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/turns/" + secondTurnId + "/submit-word")
                        .header("Authorization", "Bearer " + player.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("word", "Moon"))))
                .andExpect(status().isOk())
                .andReturn();
        String thirdTurnId = (String) responseBody(secondSubmit, "currentTurn").get("turnId");

        mockMvc.perform(get("/api/v1/games/" + started.gameId())
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId", equalTo(started.gameId())))
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.currentTurnNumber", equalTo(3)))
                .andExpect(jsonPath("$.currentTurn.turnId", equalTo(thirdTurnId)))
                .andExpect(jsonPath("$.currentTurn.playerUserId", equalTo(host.userId().toString())))
                .andExpect(jsonPath("$.turns.length()", equalTo(3)))
                .andExpect(jsonPath("$.turns[0].status", equalTo("SUBMITTED")))
                .andExpect(jsonPath("$.turns[1].status", equalTo("SUBMITTED")))
                .andExpect(jsonPath("$.turns[2].status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.storySegments.length()", equalTo(3)))
                .andExpect(jsonPath("$.storySegments[1].content", equalTo("The word \"dragon\" pushes the story into a stranger turn.")))
                .andExpect(jsonPath("$.storySegments[2].content", equalTo("The word \"moon\" pushes the story into a stranger turn.")))
                .andExpect(jsonPath("$.fullStory", equalTo("The story begins, waiting for the first word.\n\nThe word \"dragon\" pushes the story into a stranger turn.\n\nThe word \"moon\" pushes the story into a stranger turn.")));
    }

    @Test
    void submitWordRejectsNonCurrentPlayerAndMultiWordInput() throws Exception {
        AuthResponse host = register("host-submit-reject-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-submit-reject-" + UUID.randomUUID() + "@example.com", "Player");

        StartedGame started = startTwoPlayerGame(host, player, 10);

        mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/turns/" + started.turnId() + "/submit-word")
                        .header("Authorization", "Bearer " + player.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("word", "Dragon"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", equalTo("ACCESS_DENIED")));

        mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/turns/" + started.turnId() + "/submit-word")
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("word", "two words"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", equalTo("VALIDATION_FAILED")));

        mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/turns/" + started.turnId() + "/submit-word")
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("word", "murder"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", equalTo("VALIDATION_FAILED")));

        mockMvc.perform(get("/api/v1/games/" + started.gameId())
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTurn.turnId", equalTo(started.turnId())))
                .andExpect(jsonPath("$.currentTurn.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.turns.length()", equalTo(1)))
                .andExpect(jsonPath("$.storySegments.length()", equalTo(1)));
    }

    @Test
    void gameMovesToVotingAfterTurnLimit() throws Exception {
        AuthResponse host = register("host-voting-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-voting-" + UUID.randomUUID() + "@example.com", "Player");

        StartedGame started = startTwoPlayerGame(host, player, 2);
        MvcResult firstSubmit = mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/turns/" + started.turnId() + "/submit-word")
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("word", "spark"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.currentTurnNumber", equalTo(2)))
                .andReturn();
        String secondTurnId = (String) responseBody(firstSubmit, "currentTurn").get("turnId");

        mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/turns/" + secondTurnId + "/submit-word")
                        .header("Authorization", "Bearer " + player.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("word", "moon"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("VOTING")))
                .andExpect(jsonPath("$.currentTurnNumber", equalTo(2)))
                .andExpect(jsonPath("$.completedAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.currentTurn.status", equalTo("SUBMITTED")))
                .andExpect(jsonPath("$.turns.length()", equalTo(2)))
                .andExpect(jsonPath("$.turns[0].status", equalTo("SUBMITTED")))
                .andExpect(jsonPath("$.turns[0].submittedAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.turns[1].status", equalTo("SUBMITTED")))
                .andExpect(jsonPath("$.turns[1].submittedAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.fullStory", equalTo("The story begins, waiting for the first word.\n\nThe word \"spark\" pushes the story into a stranger turn.\n\nThe word \"moon\" pushes the story into a stranger turn.")))
                .andExpect(jsonPath("$.storySegments.length()", equalTo(3)));

        mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/turns/" + secondTurnId + "/submit-word")
                        .header("Authorization", "Bearer " + player.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("word", "again"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("TURN_NOT_ACTIVE")));
    }

    @Test
    void expiredTurnCanBeSkippedAndAdvancesTurnOrder() throws Exception {
        AuthResponse host = register("host-skip-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-skip-" + UUID.randomUUID() + "@example.com", "Player");

        StartedGame started = startTwoPlayerGame(host, player, 10);

        mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/turns/" + started.turnId() + "/skip-expired")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("TURN_NOT_EXPIRED")));

        expireTurn(started.turnId());

        mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/turns/" + started.turnId() + "/skip-expired")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.currentTurnNumber", equalTo(2)))
                .andExpect(jsonPath("$.currentTurn.turnNumber", equalTo(2)))
                .andExpect(jsonPath("$.currentTurn.playerUserId", equalTo(player.userId().toString())))
                .andExpect(jsonPath("$.turns.length()", equalTo(2)))
                .andExpect(jsonPath("$.turns[0].status", equalTo("SKIPPED")))
                .andExpect(jsonPath("$.turns[0].submittedAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.turns[1].status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.turns[1].submittedAt", nullValue()))
                .andExpect(jsonPath("$.storySegments.length()", equalTo(1)));

        mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/turns/" + started.turnId() + "/submit-word")
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("word", "late"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("TURN_NOT_ACTIVE")));
    }

    private ResultActions createRoom(String accessToken) throws Exception {
        return createRoom(accessToken, 10);
    }

    private ResultActions createRoom(String accessToken, int turnLimit) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "writingStyle", "FUNNY",
                        "language", "en",
                        "safetyMode", "TEEN",
                        "maxPlayers", 2,
                        "turnLimit", turnLimit,
                        "turnTimeoutSeconds", 30,
                        "visibility", "PRIVATE"))));
    }

    private StartedGame startTwoPlayerGame(AuthResponse host, AuthResponse player, int turnLimit) throws Exception {
        MvcResult createResult = createRoom(host.accessToken(), turnLimit).andExpect(status().isOk()).andReturn();
        Map<String, Object> room = responseBody(createResult);
        String roomCode = (String) room.get("roomCode");
        String roomId = (String) room.get("roomId");

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        MvcResult startResult = mockMvc.perform(post("/api/v1/rooms/" + roomId + "/games/start")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> game = responseBody(startResult);
        Map<String, Object> turn = responseBody(startResult, "currentTurn");
        return new StartedGame((String) game.get("gameId"), (String) turn.get("turnId"));
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

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private Map<String, Object> responseBody(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsByteArray(),
                new TypeReference<>() {
                });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> responseBody(MvcResult result, String field) throws Exception {
        return (Map<String, Object>) responseBody(result).get(field);
    }

    private void expireTurn(String turnId) {
        jdbcTemplate.update(
                "UPDATE game_turns SET expires_at = now() - interval '1 second' WHERE id = ?",
                UUID.fromString(turnId));
    }

    private record StartedGame(String gameId, String turnId) {
    }
}
