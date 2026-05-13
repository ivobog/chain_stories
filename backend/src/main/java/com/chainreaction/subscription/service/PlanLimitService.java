package com.chainreaction.subscription.service;

import org.springframework.stereotype.Service;

import com.chainreaction.subscription.api.EntitlementFeatures;
import com.chainreaction.subscription.domain.SubscriptionPlan;

@Service
public class PlanLimitService {

    public EntitlementFeatures featuresFor(SubscriptionPlan plan) {
        return switch (plan) {
            case FREE -> new EntitlementFeatures(2, false, false, false, false);
            case PLUS -> new EntitlementFeatures(8, true, true, false, false);
            case CREATOR, ADMIN -> new EntitlementFeatures(20, true, true, true, true);
        };
    }
}
