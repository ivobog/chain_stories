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
import com.chainreaction.room.domain.WritingStyle;
import com.chainreaction.vote.VoteCategory;
import com.chainreaction.vote.VoteRepository;
import com.chainreaction.vote.VoteResultRepository;
import com.chainreaction.word.WordRegistryEntryRepository;
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

    @Autowired
    private WordRegistryEntryRepository wordRegistryEntryRepository;

    @Autowired
    private WordSuggestionEventRepository wordSuggestionEventRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private VoteResultRepository voteResultRepository;

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

    private record StartedGame(String gameId, String turnId) {
    }
}
