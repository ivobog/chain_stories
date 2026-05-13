package com.chainreaction.subscription.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chainreaction.subscription.api.EntitlementFeatures;
import com.chainreaction.subscription.api.EntitlementsResponse;
import com.chainreaction.subscription.domain.SubscriptionPlan;
import com.chainreaction.subscription.repository.SubscriptionRepository;

@Service
public class EntitlementService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanLimitService planLimitService;

    public EntitlementService(SubscriptionRepository subscriptionRepository, PlanLimitService planLimitService) {
        this.subscriptionRepository = subscriptionRepository;
        this.planLimitService = planLimitService;
    }

    @Transactional(readOnly = true)
    public EntitlementsResponse getEntitlements(UUID userId) {
        SubscriptionPlan plan = effectivePlan(userId);
        return new EntitlementsResponse(plan, planLimitService.featuresFor(plan));
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan effectivePlan(UUID userId) {
        return subscriptionRepository.findByUserId(userId)
                .map(subscription -> subscription.effectivePlan())
                .orElse(SubscriptionPlan.FREE);
    }

    @Transactional(readOnly = true)
    public EntitlementFeatures features(UUID userId) {
        return planLimitService.featuresFor(effectivePlan(userId));
    }
}
