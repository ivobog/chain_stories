package com.chainreaction.subscription.domain;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SubscriptionStatus status;

    @Column(length = 64)
    private String provider;

    @Column(name = "provider_subscription_id", length = 160)
    private String providerSubscriptionId;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Subscription() {
    }

    public Subscription(User user, SubscriptionPlan plan) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.plan = plan;
        this.status = SubscriptionStatus.ACTIVE;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public SubscriptionPlan effectivePlan() {
        if (status != SubscriptionStatus.ACTIVE) {
            return SubscriptionPlan.FREE;
        }
        if (currentPeriodEnd != null && currentPeriodEnd.isBefore(Instant.now())) {
            return SubscriptionPlan.FREE;
        }
        return plan;
    }

    public void activate(
            SubscriptionPlan plan,
            String provider,
            String providerSubscriptionId,
            Instant currentPeriodEnd) {
        this.plan = plan;
        this.status = SubscriptionStatus.ACTIVE;
        this.provider = provider;
        this.providerSubscriptionId = providerSubscriptionId;
        this.currentPeriodEnd = currentPeriodEnd;
    }

    public void cancel() {
        this.status = SubscriptionStatus.CANCELLED;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }
}
