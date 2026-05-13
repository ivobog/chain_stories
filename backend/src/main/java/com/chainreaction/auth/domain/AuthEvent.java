package com.chainreaction.auth.domain;

import java.time.Instant;
import java.util.UUID;

import com.chainreaction.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_events")
public class AuthEvent {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 320)
    private String email;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 32)
    private String outcome;

    @Column(length = 120)
    private String reason;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuthEvent() {
    }

    public AuthEvent(User user, String email, String eventType, String outcome, String reason, String correlationId) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.email = email;
        this.eventType = eventType;
        this.outcome = outcome;
        this.reason = reason;
        this.correlationId = correlationId;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
