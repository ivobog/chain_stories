package com.chainreaction.game.api;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import com.chainreaction.ai.WordSuggestionEventRepository;
import com.chainreaction.auth.api.AuthResponse;
import com.chainreaction.moderation.ModerationEventOutcome;
import com.chainreaction.moderation.ModerationEventRepository;
import com.chainreaction.moderation.ModerationEventSource;
import com.chainreaction.room.domain.WritingStyle;
import com.chainreaction.vote.VoteCategory;
import com.chainreaction.vote.VoteRepository;
import com.chainreaction.vote.VoteResultRepository;
import com.chainreaction.word.WordRegistryEntryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "app.bot.auto-submit-delay-ms=0")
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

    @Autowired
    private WordRegistryEntryRepository wordRegistryEntryRepository;

    @Autowired
    private WordSuggestionEventRepository wordSuggestionEventRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private VoteResultRepository voteResultRepository;

    @Autowired
    private ModerationEventRepository moderationEventRepository;

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

        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/game")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId", equalTo(gameId)))
                .andExpect(jsonPath("$.roomId", equalTo(roomId)))
                .andExpect(jsonPath("$.currentTurn.playerUserId", equalTo(host.userId().toString())))
                .andExpect(jsonPath("$.fullStory", equalTo("The story begins, waiting for the first word.")));

        mockMvc.perform(get("/api/v1/rooms/" + roomId)
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")));
    }

    @Test
    void authenticatedUserCanCreateAndStartPlayWithBotGame() throws Exception {
        AuthResponse host = register("host-bot-game-" + UUID.randomUUID() + "@example.com", "Host");

        mockMvc.perform(post("/api/v1/games/play-with-bot")
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "writingStyle", "FUNNY",
                                "language", "en",
                                "safetyMode", "FAMILY",
                                "turnLimit", 10,
                                "turnTimeoutSeconds", 60))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.room.roomId", not(blankOrNullString())))
                .andExpect(jsonPath("$.room.roomCode", not(blankOrNullString())))
                .andExpect(jsonPath("$.room.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.room.settings.writingStyle", equalTo("FUNNY")))
                .andExpect(jsonPath("$.room.settings.language", equalTo("en")))
                .andExpect(jsonPath("$.room.settings.safetyMode", equalTo("FAMILY")))
                .andExpect(jsonPath("$.room.settings.maxPlayers", equalTo(2)))
                .andExpect(jsonPath("$.room.settings.turnLimit", equalTo(10)))
                .andExpect(jsonPath("$.room.settings.turnTimeoutSeconds", equalTo(60)))
                .andExpect(jsonPath("$.room.settings.visibility", equalTo("PRIVATE")))
                .andExpect(jsonPath("$.room.participants.length()", equalTo(2)))
                .andExpect(jsonPath("$.room.participants[0].userId", equalTo(host.userId().toString())))
                .andExpect(jsonPath("$.room.participants[0].displayName", equalTo("Host")))
                .andExpect(jsonPath("$.room.participants[0].participantType", equalTo("HUMAN")))
                .andExpect(jsonPath("$.room.participants[0].role", equalTo("HOST")))
                .andExpect(jsonPath("$.room.participants[1].displayName", equalTo("StoryBot")))
                .andExpect(jsonPath("$.room.participants[1].participantType", equalTo("BOT")))
                .andExpect(jsonPath("$.room.participants[1].role", equalTo("PLAYER")))
                .andExpect(jsonPath("$.game.gameId", not(blankOrNullString())))
                .andExpect(jsonPath("$.game.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.game.currentTurnNumber", equalTo(1)))
                .andExpect(jsonPath("$.game.currentTurn.turnNumber", equalTo(1)))
                .andExpect(jsonPath("$.game.currentTurn.playerUserId", equalTo(host.userId().toString())))
                .andExpect(jsonPath("$.game.turnOrder.length()", equalTo(2)))
                .andExpect(jsonPath("$.game.turnOrder[0]", equalTo(host.userId().toString())))
                .andExpect(jsonPath("$.game.storySegments.length()", equalTo(1)))
                .andExpect(jsonPath("$.game.storySegments[0].content",
                        equalTo("The story begins, waiting for the first word.")));
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

        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/game")
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
                .andExpect(jsonPath("$.storySegments[1].playedWord", equalTo("Dragon")))
                .andExpect(jsonPath("$.storySegments[1].playedWordNormalized", equalTo("dragon")))
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

        var registryEntries = wordRegistryEntryRepository.findAllByGameIdOrderByCreatedAtAsc(UUID.fromString(started.gameId()));
        org.assertj.core.api.Assertions.assertThat(registryEntries).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(registryEntries.get(0).getTurnId()).isEqualTo(UUID.fromString(started.turnId()));
        org.assertj.core.api.Assertions.assertThat(registryEntries.get(0).getPlayerUserId()).isEqualTo(host.userId());
        org.assertj.core.api.Assertions.assertThat(registryEntries.get(0).getNormalizedWord()).isEqualTo("dragon");
        org.assertj.core.api.Assertions.assertThat(registryEntries.get(0).getWritingStyle()).isEqualTo(WritingStyle.FUNNY);
        org.assertj.core.api.Assertions.assertThat(registryEntries.get(0).getLanguage()).isEqualTo("en");
        org.assertj.core.api.Assertions.assertThat(registryEntries.get(0).getGeneratedSentence())
                .isEqualTo("The word \"dragon\" pushes the story into a stranger turn.");
    }

    @Test
    void botTurnAutoSubmitsAfterHumanTurnInPlayWithBotGame() throws Exception {
        AuthResponse host = register("host-bot-auto-" + UUID.randomUUID() + "@example.com", "Host");

        MvcResult playWithBotResult = mockMvc.perform(post("/api/v1/games/play-with-bot")
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "writingStyle", "FUNNY",
                                "language", "en",
                                "safetyMode", "TEEN",
                                "turnLimit", 2,
                                "turnTimeoutSeconds", 60))))
                .andExpect(status().isCreated())
                .andReturn();

        Map<String, Object> game = responseBody(playWithBotResult, "game");
        Map<String, Object> turn = (Map<String, Object>) game.get("currentTurn");
        String gameId = (String) game.get("gameId");
        String turnId = (String) turn.get("turnId");

        mockMvc.perform(post("/api/v1/games/" + gameId + "/turns/" + turnId + "/submit-word")
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("word", "Dragon"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.currentTurnNumber", equalTo(2)))
                .andExpect(jsonPath("$.storySegments.length()", equalTo(2)))
                .andExpect(jsonPath("$.storySegments[1].playedWord", equalTo("Dragon")))
                .andExpect(jsonPath("$.storySegments[1].playedWordNormalized", equalTo("dragon")));

        MvcResult finalState = awaitGameState(host.accessToken(), gameId, current -> {
            try {
                return "VOTING".equals(current.get("status"))
                        && ((List<?>) current.get("storySegments")).size() == 3;
            } catch (ClassCastException exception) {
                return false;
            }
        });

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> segments = (List<Map<String, Object>>) responseBody(finalState).get("storySegments");
        org.assertj.core.api.Assertions.assertThat(segments).hasSize(3);
        org.assertj.core.api.Assertions.assertThat(responseBody(finalState).get("status")).isEqualTo("VOTING");
        org.assertj.core.api.Assertions.assertThat(segments.get(2).get("playedWord")).isNotNull();
        org.assertj.core.api.Assertions.assertThat(segments.get(2).get("playedWordNormalized")).isNotNull();
        org.assertj.core.api.Assertions.assertThat(segments.get(2).get("authorUserId")).isNotEqualTo(host.userId().toString());

        var registryEntries = wordRegistryEntryRepository.findAllByGameIdOrderByCreatedAtAsc(UUID.fromString(gameId));
        org.assertj.core.api.Assertions.assertThat(registryEntries).hasSize(2);
    }

    @Test
    void currentPlayerCanRequestRandomWordSuggestion() throws Exception {
        AuthResponse host = register("host-random-word-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-random-word-" + UUID.randomUUID() + "@example.com", "Player");

        StartedGame started = startTwoPlayerGame(host, player, 10);

        MvcResult suggestionResult = mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/random-word")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.word", not(blankOrNullString())))
                .andExpect(jsonPath("$.normalizedWord", not(blankOrNullString())))
                .andExpect(jsonPath("$.safetyLevel", equalTo("TEEN")))
                .andExpect(jsonPath("$.writingStyle", equalTo("FUNNY")))
                .andExpect(jsonPath("$.language", equalTo("en")))
                .andReturn();

        Map<String, Object> suggestion = responseBody(suggestionResult);
        var events = wordSuggestionEventRepository.findAllByGameIdOrderByCreatedAtAsc(UUID.fromString(started.gameId()));
        org.assertj.core.api.Assertions.assertThat(events).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(events.get(0).getTurnId()).isEqualTo(UUID.fromString(started.turnId()));
        org.assertj.core.api.Assertions.assertThat(events.get(0).getPlayerUserId()).isEqualTo(host.userId());
        org.assertj.core.api.Assertions.assertThat(events.get(0).getSuggestedWord()).isEqualTo(suggestion.get("word"));
        org.assertj.core.api.Assertions.assertThat(events.get(0).getNormalizedWord()).isEqualTo(suggestion.get("normalizedWord"));
        org.assertj.core.api.Assertions.assertThat(events.get(0).getWritingStyle()).isEqualTo(WritingStyle.FUNNY);
        org.assertj.core.api.Assertions.assertThat(events.get(0).getLanguage()).isEqualTo("en");
        org.assertj.core.api.Assertions.assertThat(events.get(0).getSafetyLevel()).isEqualTo("TEEN");
        org.assertj.core.api.Assertions.assertThat(events.get(0).getCurrentStoryCharacters()).isPositive();
        org.assertj.core.api.Assertions.assertThat(events.get(0).getPreviousWordsCount()).isZero();

        mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/random-word")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", equalTo("ACCESS_DENIED")));

        org.assertj.core.api.Assertions.assertThat(
                wordSuggestionEventRepository.findAllByGameIdOrderByCreatedAtAsc(UUID.fromString(started.gameId())))
                .hasSize(1);
    }

    @Test
    void randomWordSuggestionIsRateLimited() throws Exception {
        AuthResponse host = register("host-random-limit-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-random-limit-" + UUID.randomUUID() + "@example.com", "Player");

        StartedGame started = startTwoPlayerGame(host, player, 10);

        for (int request = 0; request < 3; request++) {
            mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/random-word")
                            .header("Authorization", "Bearer " + host.accessToken()))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/random-word")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode", equalTo("RATE_LIMITED")));

        org.assertj.core.api.Assertions.assertThat(
                wordSuggestionEventRepository.findAllByGameIdOrderByCreatedAtAsc(UUID.fromString(started.gameId())))
                .hasSize(3);
    }

    @Test
    void submitWordAttemptsAreRateLimited() throws Exception {
        AuthResponse host = register("host-submit-limit-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-submit-limit-" + UUID.randomUUID() + "@example.com", "Player");

        StartedGame started = startTwoPlayerGame(host, player, 10);

        for (int request = 0; request < 5; request++) {
            mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/turns/" + started.turnId() + "/submit-word")
                            .header("Authorization", "Bearer " + host.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("word", "two words"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", equalTo("VALIDATION_FAILED")));
        }

        mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/turns/" + started.turnId() + "/submit-word")
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("word", "two words"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode", equalTo("RATE_LIMITED")));

        org.assertj.core.api.Assertions.assertThat(
                aiGenerationAttemptRepository.findAllByGameIdOrderByCreatedAtAsc(UUID.fromString(started.gameId())))
                .isEmpty();
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

        org.assertj.core.api.Assertions.assertThat(
                wordRegistryEntryRepository.findAllByGameIdOrderByCreatedAtAsc(UUID.fromString(started.gameId())))
                .isEmpty();
    }

    @Test
    void blockedSubmittedWordCreatesModerationAuditEvent() throws Exception {
        AuthResponse host = register("host-moderation-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-moderation-" + UUID.randomUUID() + "@example.com", "Player");

        StartedGame started = startTwoPlayerGame(host, player, 10);

        mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/turns/" + started.turnId() + "/submit-word")
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("word", "murder"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", equalTo("VALIDATION_FAILED")));

        var events = moderationEventRepository.findAllByGameIdOrderByCreatedAtAsc(UUID.fromString(started.gameId()));
        org.assertj.core.api.Assertions.assertThat(events).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(events.get(0).getSource()).isEqualTo(ModerationEventSource.SUBMITTED_WORD);
        org.assertj.core.api.Assertions.assertThat(events.get(0).getOutcome()).isEqualTo(ModerationEventOutcome.BLOCKED);
        org.assertj.core.api.Assertions.assertThat(events.get(0).getTurnId()).isEqualTo(UUID.fromString(started.turnId()));
        org.assertj.core.api.Assertions.assertThat(events.get(0).getPlayerUserId()).isEqualTo(host.userId());
        org.assertj.core.api.Assertions.assertThat(events.get(0).getReason())
                .isEqualTo("Submitted word is not allowed for this room.");
        org.assertj.core.api.Assertions.assertThat(events.get(0).getContentExcerpt()).isEqualTo("murder");
    }

    @Test
    void adminCanReviewModerationEvents() throws Exception {
        AuthResponse host = register("host-admin-moderation-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-admin-moderation-" + UUID.randomUUID() + "@example.com", "Player");

        StartedGame started = startTwoPlayerGame(host, player, 10);

        mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/turns/" + started.turnId() + "/submit-word")
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("word", "murder"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/moderation/events")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isForbidden());

        jdbcTemplate.update("UPDATE users SET role = 'ROLE_ADMIN' WHERE id = ?", host.userId());

        mockMvc.perform(get("/api/v1/admin/moderation/events")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gameId", equalTo(started.gameId())))
                .andExpect(jsonPath("$[0].turnId", equalTo(started.turnId())))
                .andExpect(jsonPath("$[0].playerUserId", equalTo(host.userId().toString())))
                .andExpect(jsonPath("$[0].source", equalTo("SUBMITTED_WORD")))
                .andExpect(jsonPath("$[0].outcome", equalTo("BLOCKED")))
                .andExpect(jsonPath("$[0].reason", equalTo("Submitted word is not allowed for this room.")));
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
    void playersCanVoteOncePerCategoryWhenGameIsInVoting() throws Exception {
        AuthResponse host = register("host-vote-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("player-vote-" + UUID.randomUUID() + "@example.com", "Player");

        StartedGame started = startTwoPlayerGame(host, player, 2);

        mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/votes")
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "category", "MVP_PLAYER",
                                "targetUserId", player.userId().toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("VOTING_NOT_OPEN")));

        MvcResult firstSubmit = mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/turns/" + started.turnId() + "/submit-word")
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("word", "spark"))))
                .andExpect(status().isOk())
                .andReturn();
        String secondTurnId = (String) responseBody(firstSubmit, "currentTurn").get("turnId");

        MvcResult votingResult = mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/turns/" + secondTurnId + "/submit-word")
                        .header("Authorization", "Bearer " + player.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("word", "moon"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("VOTING")))
                .andReturn();
        String firstPlayableSegmentId = storySegmentId(votingResult, 1);
        String secondPlayableSegmentId = storySegmentId(votingResult, 2);

        mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/votes")
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "category", "FUNNIEST_WORD",
                                "targetStorySegmentId", firstPlayableSegmentId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId", equalTo(started.gameId())))
                .andExpect(jsonPath("$.voterUserId", equalTo(host.userId().toString())))
                .andExpect(jsonPath("$.category", equalTo("FUNNIEST_WORD")))
                .andExpect(jsonPath("$.targetStorySegmentId", equalTo(firstPlayableSegmentId)))
                .andExpect(jsonPath("$.targetUserId", nullValue()));

        mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/votes")
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "category", "FUNNIEST_WORD",
                                "targetStorySegmentId", firstPlayableSegmentId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("DUPLICATE_VOTE")));

        mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/votes")
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "category", "MVP_PLAYER",
                                "targetUserId", player.userId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category", equalTo("MVP_PLAYER")))
                .andExpect(jsonPath("$.targetUserId", equalTo(player.userId().toString())))
                .andExpect(jsonPath("$.targetStorySegmentId", nullValue()));

        var votes = voteRepository.findAllByGameIdOrderByCreatedAtAsc(UUID.fromString(started.gameId()));
        org.assertj.core.api.Assertions.assertThat(votes).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(votes)
                .extracting(vote -> vote.getCategory())
                .containsExactly(VoteCategory.FUNNIEST_WORD, VoteCategory.MVP_PLAYER);

        var persistedResults = voteResultRepository.findAllByGameIdOrderByCategoryAscResultRankAsc(UUID.fromString(started.gameId()));
        org.assertj.core.api.Assertions.assertThat(persistedResults).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(persistedResults)
                .extracting(result -> result.getCategory())
                .containsExactly(VoteCategory.FUNNIEST_WORD, VoteCategory.MVP_PLAYER);
        org.assertj.core.api.Assertions.assertThat(persistedResults.get(0).getTargetStorySegmentId())
                .isEqualTo(UUID.fromString(firstPlayableSegmentId));
        org.assertj.core.api.Assertions.assertThat(persistedResults.get(0).getVoteCount()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(persistedResults.get(1).getTargetUserId()).isEqualTo(player.userId());
        org.assertj.core.api.Assertions.assertThat(persistedResults.get(1).getVoteCount()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/games/" + started.gameId() + "/votes/results")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId", equalTo(started.gameId())))
                .andExpect(jsonPath("$.categories.length()", equalTo(5)))
                .andExpect(jsonPath("$.categories[0].category", equalTo("FUNNIEST_WORD")))
                .andExpect(jsonPath("$.categories[0].results.length()", equalTo(1)))
                .andExpect(jsonPath("$.categories[0].results[0].targetStorySegmentId", equalTo(firstPlayableSegmentId)))
                .andExpect(jsonPath("$.categories[0].results[0].targetUserId", nullValue()))
                .andExpect(jsonPath("$.categories[0].results[0].voteCount", equalTo(1)))
                .andExpect(jsonPath("$.categories[4].category", equalTo("MVP_PLAYER")))
                .andExpect(jsonPath("$.categories[4].results.length()", equalTo(1)))
                .andExpect(jsonPath("$.categories[4].results[0].targetUserId", equalTo(player.userId().toString())))
                .andExpect(jsonPath("$.categories[4].results[0].targetStorySegmentId", nullValue()))
                .andExpect(jsonPath("$.categories[4].results[0].voteCount", equalTo(1)));

        submitStoryVote(host, started.gameId(), VoteCategory.WEIRDEST_TWIST, firstPlayableSegmentId);
        submitStoryVote(host, started.gameId(), VoteCategory.BEST_AI_SENTENCE, secondPlayableSegmentId);
        submitPlayerVote(host, started.gameId(), VoteCategory.BEST_SABOTAGE, player.userId());
        submitStoryVote(player, started.gameId(), VoteCategory.FUNNIEST_WORD, secondPlayableSegmentId);
        submitStoryVote(player, started.gameId(), VoteCategory.WEIRDEST_TWIST, firstPlayableSegmentId);
        submitStoryVote(player, started.gameId(), VoteCategory.BEST_AI_SENTENCE, secondPlayableSegmentId);
        submitPlayerVote(player, started.gameId(), VoteCategory.BEST_SABOTAGE, host.userId());
        submitPlayerVote(player, started.gameId(), VoteCategory.MVP_PLAYER, host.userId());

        org.assertj.core.api.Assertions.assertThat(voteRepository.countByGameId(UUID.fromString(started.gameId())))
                .isEqualTo(10);
        mockMvc.perform(get("/api/v1/games/" + started.gameId())
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("FINISHED")));

        mockMvc.perform(get("/api/v1/games/" + started.gameId() + "/votes/results")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId", equalTo(started.gameId())))
                .andExpect(jsonPath("$.categories[0].results.length()", equalTo(2)))
                .andExpect(jsonPath("$.categories[0].results[0].voteCount", equalTo(1)))
                .andExpect(jsonPath("$.categories[1].results.length()", equalTo(2)))
                .andExpect(jsonPath("$.categories[4].results.length()", equalTo(2)));

        mockMvc.perform(post("/api/v1/games/" + started.gameId() + "/votes")
                        .header("Authorization", "Bearer " + player.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "category", "MVP_PLAYER",
                                "targetUserId", player.userId().toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("VOTING_NOT_OPEN")));
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

    @SuppressWarnings("unchecked")
    private String storySegmentId(MvcResult result, int index) throws Exception {
        List<Map<String, Object>> segments = (List<Map<String, Object>>) responseBody(result).get("storySegments");
        return (String) segments.get(index).get("segmentId");
    }

    private void submitStoryVote(AuthResponse voter, String gameId, VoteCategory category, String targetStorySegmentId) throws Exception {
        mockMvc.perform(post("/api/v1/games/" + gameId + "/votes")
                        .header("Authorization", "Bearer " + voter.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "category", category.name(),
                                "targetStorySegmentId", targetStorySegmentId))))
                .andExpect(status().isOk());
    }

    private void submitPlayerVote(AuthResponse voter, String gameId, VoteCategory category, UUID targetUserId) throws Exception {
        mockMvc.perform(post("/api/v1/games/" + gameId + "/votes")
                        .header("Authorization", "Bearer " + voter.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "category", category.name(),
                                "targetUserId", targetUserId.toString()))))
                .andExpect(status().isOk());
    }

    private void expireTurn(String turnId) {
        jdbcTemplate.update(
                "UPDATE game_turns SET expires_at = now() - interval '1 second' WHERE id = ?",
                UUID.fromString(turnId));
    }

    private MvcResult awaitGameState(
            String accessToken,
            String gameId,
            java.util.function.Predicate<Map<String, Object>> condition) throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            MvcResult result = mockMvc.perform(get("/api/v1/games/" + gameId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andReturn();
            if (condition.test(responseBody(result))) {
                return result;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for expected game state.");
    }

    private record StartedGame(String gameId, String turnId) {
    }
}
