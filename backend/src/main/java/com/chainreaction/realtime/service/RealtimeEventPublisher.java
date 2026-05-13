package com.chainreaction.realtime.service;

import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.chainreaction.realtime.api.RealtimeEvent;
import com.chainreaction.realtime.api.RealtimeEventType;

@Service
public class RealtimeEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishRoomEvent(UUID roomId, RealtimeEventType type, Object payload) {
        sendAfterCommitOrNow(() ->
                messagingTemplate.convertAndSend(roomTopic(roomId), RealtimeEvent.room(type, roomId, payload)));
    }

    public void publishGameEvent(UUID roomId, UUID gameId, RealtimeEventType type, Object payload) {
        sendAfterCommitOrNow(() ->
                messagingTemplate.convertAndSend(roomTopic(roomId), RealtimeEvent.game(type, roomId, gameId, payload)));
    }

    public void publishUserEvent(String username, RealtimeEventType type, Object payload) {
        sendAfterCommitOrNow(() ->
                messagingTemplate.convertAndSendToUser(username, "/queue/events", RealtimeEvent.user(type, payload)));
    }

    private String roomTopic(UUID roomId) {
        return "/topic/rooms/" + roomId;
    }

    private void sendAfterCommitOrNow(Runnable send) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send.run();
            }
        });
    }
}
