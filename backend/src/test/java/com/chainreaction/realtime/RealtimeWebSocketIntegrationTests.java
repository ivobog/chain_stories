package com.chainreaction.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.chainreaction.auth.api.AuthResponse;
import com.chainreaction.realtime.api.RealtimeEvent;
import com.chainreaction.realtime.api.RealtimeEventType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RealtimeWebSocketIntegrationTests {

    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(5);

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final WebSocketStompClient stompClient = stompClient();

    @AfterEach
    void stopClient() {
        stompClient.stop();
    }

    @Test
    void hostReceivesPlayerJoinedEventOverRoomTopic() throws Exception {
        AuthResponse host = register("ws-host-join-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("ws-player-join-" + UUID.randomUUID() + "@example.com", "Player");

        MvcResult createResult = createRoom(host.accessToken());
        Map<String, Object> room = responseBody(createResult);
        String roomId = (String) room.get("roomId");
        String roomCode = (String) room.get("roomCode");

        BlockingQueue<RealtimeEvent> hostEvents = new LinkedBlockingQueue<>();
        StompSession hostSession = connect(host.accessToken());
        subscribe(hostSession, "/topic/rooms/" + roomId, hostEvents);

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        RealtimeEvent event = take(hostEvents);
        assertThat(event.type()).isEqualTo(RealtimeEventType.PLAYER_JOINED);
        assertThat(event.roomId()).isEqualTo(UUID.fromString(roomId));
        assertThat(event.gameId()).isNull();
        assertThat(event.payload()).isNotNull();

        hostSession.disconnect();
    }

    @Test
    void twoParticipantsReceiveGameEventsOverRoomTopic() throws Exception {
        AuthResponse host = register("ws-host-game-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("ws-player-game-" + UUID.randomUUID() + "@example.com", "Player");

        MvcResult createResult = createRoom(host.accessToken());
        Map<String, Object> room = responseBody(createResult);
        String roomId = (String) room.get("roomId");
        String roomCode = (String) room.get("roomCode");

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        BlockingQueue<RealtimeEvent> hostEvents = new LinkedBlockingQueue<>();
        BlockingQueue<RealtimeEvent> playerEvents = new LinkedBlockingQueue<>();
        StompSession hostSession = connect(host.accessToken());
        StompSession playerSession = connect(player.accessToken());
        subscribe(hostSession, "/topic/rooms/" + roomId, hostEvents);
        subscribe(playerSession, "/topic/rooms/" + roomId, playerEvents);

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/games/start")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk());

        assertThat(takeTypes(hostEvents, 2))
                .containsExactlyInAnyOrder(RealtimeEventType.GAME_STARTED, RealtimeEventType.TURN_STARTED);
        assertThat(takeTypes(playerEvents, 2))
                .containsExactlyInAnyOrder(RealtimeEventType.GAME_STARTED, RealtimeEventType.TURN_STARTED);

        hostSession.disconnect();
        playerSession.disconnect();
    }

    @Test
    void kickedParticipantReceivesPrivateUserQueueEvent() throws Exception {
        AuthResponse host = register("ws-host-private-" + UUID.randomUUID() + "@example.com", "Host");
        AuthResponse player = register("ws-player-private-" + UUID.randomUUID() + "@example.com", "Player");

        MvcResult createResult = createRoom(host.accessToken());
        Map<String, Object> room = responseBody(createResult);
        String roomId = (String) room.get("roomId");
        String roomCode = (String) room.get("roomCode");

        mockMvc.perform(post("/api/v1/rooms/" + roomCode + "/join")
                        .header("Authorization", "Bearer " + player.accessToken()))
                .andExpect(status().isOk());

        BlockingQueue<RealtimeEvent> privateEvents = new LinkedBlockingQueue<>();
        StompSession playerSession = connect(player.accessToken());
        subscribe(playerSession, "/user/queue/events", privateEvents);

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/participants/" + player.userId() + "/kick")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk());

        RealtimeEvent event = take(privateEvents);
        assertThat(event.type()).isEqualTo(RealtimeEventType.PLAYER_KICKED);
        assertThat(event.roomId()).isNull();
        assertThat(event.gameId()).isNull();
        assertThat(event.payload()).isNotNull();

        playerSession.disconnect();
    }

    private StompSession connect(String accessToken) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        return stompClient.connectAsync(
                        "ws://localhost:" + port + "/ws/game",
                        new WebSocketHttpHeaders(),
                        headers,
                        new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS);
    }

    private void subscribe(StompSession session, String destination, BlockingQueue<RealtimeEvent> events)
            throws InterruptedException {
        session.subscribe(destination, eventHandler(events));
        Thread.sleep(500);
    }

    private StompFrameHandler eventHandler(BlockingQueue<RealtimeEvent> events) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                try {
                    events.add(objectMapper.readValue((byte[]) payload, RealtimeEvent.class));
                } catch (Exception exception) {
                    throw new IllegalStateException("Could not read realtime event payload.", exception);
                }
            }
        };
    }

    private RealtimeEvent take(BlockingQueue<RealtimeEvent> events) throws InterruptedException {
        RealtimeEvent event = events.poll(EVENT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        return event;
    }

    private List<RealtimeEventType> takeTypes(BlockingQueue<RealtimeEvent> events, int count) throws InterruptedException {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> {
                    try {
                        return take(events).type();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while waiting for realtime events.", exception);
                    }
                })
                .toList();
    }

    private MvcResult createRoom(String accessToken) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "writingStyle", "FUNNY",
                                "language", "en",
                                "safetyMode", "TEEN",
                                "maxPlayers", 2,
                                "turnLimit", 10,
                                "turnTimeoutSeconds", 30,
                                "visibility", "PRIVATE"))))
                .andExpect(status().isOk())
                .andReturn();
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

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        return client;
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
