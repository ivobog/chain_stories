package com.chainreaction.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.chainreaction.realtime.api.RealtimeEvent;
import com.chainreaction.realtime.api.RealtimeEventType;

class RealtimeEventPublisherTests {

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final RealtimeEventPublisher publisher = new RealtimeEventPublisher(messagingTemplate);

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishesRoomEventsToRoomTopic() {
        UUID roomId = UUID.randomUUID();
        Map<String, String> payload = Map.of("status", "joined");

        publisher.publishRoomEvent(roomId, RealtimeEventType.PLAYER_JOINED, payload);

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/rooms/" + roomId), eventCaptor.capture());
        RealtimeEvent event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RealtimeEventType.PLAYER_JOINED);
        assertThat(event.roomId()).isEqualTo(roomId);
        assertThat(event.gameId()).isNull();
        assertThat(event.payload()).isEqualTo(payload);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void publishesGameEventsToRoomTopicWithGameId() {
        UUID roomId = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();

        publisher.publishGameEvent(roomId, gameId, RealtimeEventType.GAME_STARTED, "game");

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/rooms/" + roomId), eventCaptor.capture());
        RealtimeEvent event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RealtimeEventType.GAME_STARTED);
        assertThat(event.roomId()).isEqualTo(roomId);
        assertThat(event.gameId()).isEqualTo(gameId);
        assertThat(event.payload()).isEqualTo("game");
    }

    @Test
    void publishesUserEventsToUserQueue() {
        String username = "player@example.com";
        Map<String, String> payload = Map.of("message", "private");

        publisher.publishUserEvent(username, RealtimeEventType.ERROR_EVENT, payload);

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(messagingTemplate).convertAndSendToUser(eq(username), eq("/queue/events"), eventCaptor.capture());
        RealtimeEvent event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RealtimeEventType.ERROR_EVENT);
        assertThat(event.roomId()).isNull();
        assertThat(event.gameId()).isNull();
        assertThat(event.payload()).isEqualTo(payload);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void defersEventsUntilTransactionCommit() {
        UUID roomId = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishRoomEvent(roomId, RealtimeEventType.PLAYER_JOINED, "joined");

        verifyNoInteractions(messagingTemplate);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/rooms/" + roomId), eventCaptor.capture());
        assertThat(eventCaptor.getValue().payload()).isEqualTo("joined");
    }

    @Test
    void doesNotPublishDeferredEventsWhenTransactionRollsBack() {
        UUID roomId = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishRoomEvent(roomId, RealtimeEventType.PLAYER_JOINED, "joined");

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(messagingTemplate);
    }
}
