package com.chainreaction.subscription.api;

import java.time.Instant;

import com.chainreaction.subscription.domain.SubscriptionPlan;
import com.chainreaction.subscription.domain.SubscriptionStatus;

public record SubscriptionResponse(
        SubscriptionPlan plan,
        SubscriptionStatus status,
        String provider,
        Instant currentPeriodEnd,
        EntitlementFeatures features) {
}
