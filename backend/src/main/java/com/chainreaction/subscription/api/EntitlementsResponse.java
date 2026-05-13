package com.chainreaction.subscription.api;

import com.chainreaction.subscription.domain.SubscriptionPlan;

public record EntitlementsResponse(SubscriptionPlan plan, EntitlementFeatures features) {
}
