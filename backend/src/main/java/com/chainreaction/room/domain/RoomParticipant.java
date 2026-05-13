package com.chainreaction.room.domain;

import java.time.Instant;
import java.util.UUID;

import com.chainreaction.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "room_participants")
public class RoomParticipant {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RoomParticipantRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RoomParticipantStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    protected RoomParticipant() {
    }

    public RoomParticipant(Room room, User user, RoomParticipantRole role) {
        this.id = UUID.randomUUID();
        this.room = room;
        this.user = user;
        this.role = role;
        this.status = RoomParticipantStatus.JOINED;
    }

    @PrePersist
    void prePersist() {
        this.joinedAt = Instant.now();
    }

    public void rejoin() {
        this.status = RoomParticipantStatus.JOINED;
        this.leftAt = null;
    }

    public void leave() {
        this.status = RoomParticipantStatus.LEFT;
        this.leftAt = Instant.now();
    }

    public void kick() {
        this.status = RoomParticipantStatus.KICKED;
        this.leftAt = Instant.now();
    }

    public void promoteToHost() {
        this.role = RoomParticipantRole.HOST;
    }

    public void demoteToPlayer() {
        this.role = RoomParticipantRole.PLAYER;
    }

    public UUID getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public User getUser() {
        return user;
    }

    public RoomParticipantRole getRole() {
        return role;
    }

    public RoomParticipantStatus getStatus() {
        return status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
