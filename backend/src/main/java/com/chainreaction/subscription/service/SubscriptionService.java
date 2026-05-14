package com.chainreaction.subscription.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;
import com.chainreaction.observability.ApplicationMetrics;
import com.chainreaction.subscription.api.MockPurchaseRequest;
import com.chainreaction.subscription.api.EntitlementFeatures;
import com.chainreaction.subscription.api.SubscriptionResponse;
import com.chainreaction.subscription.domain.Subscription;
import com.chainreaction.subscription.domain.SubscriptionPlan;
import com.chainreaction.subscription.domain.SubscriptionStatus;
import com.chainreaction.subscription.repository.SubscriptionRepository;
import com.chainreaction.user.domain.User;
import com.chainreaction.user.repository.UserRepository;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PlanLimitService planLimitService;
    private final ApplicationMetrics applicationMetrics;
    private final boolean mockPurchasesEnabled;

    public SubscriptionService(
            SubscriptionRepository subscriptionRepository,
            UserRepository userRepository,
            PlanLimitService planLimitService,
            ApplicationMetrics applicationMetrics,
            @Value("${app.subscriptions.mock-purchases-enabled:false}") boolean mockPurchasesEnabled) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.planLimitService = planLimitService;
        this.applicationMetrics = applicationMetrics;
        this.mockPurchasesEnabled = mockPurchasesEnabled;
    }

    @Transactional
    public SubscriptionResponse mockPurchase(UUID userId, MockPurchaseRequest request) {
        assertMockPurchasesEnabled();
        if (request.plan() == SubscriptionPlan.FREE || request.plan() == SubscriptionPlan.ADMIN) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Mock purchases support PLUS and CREATOR plans only.");
        }

        User user = requireActiveUser(userId);
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseGet(() -> new Subscription(user, request.plan()));
        subscription.activate(
                request.plan(),
                request.provider().trim().toLowerCase(),
                mockProviderSubscriptionId(userId, request),
                Instant.now().plus(30, ChronoUnit.DAYS));
        Subscription saved = subscriptionRepository.save(subscription);
        applicationMetrics.recordSubscriptionUpgrade(request.plan());
        return response(saved);
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription(UUID userId) {
        requireActiveUser(userId);
        return subscriptionRepository.findByUserId(userId)
                .map(this::response)
                .orElseGet(this::freeResponse);
    }

    @Transactional
    public SubscriptionResponse cancel(UUID userId) {
        assertMockPurchasesEnabled();
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.NOT_FOUND,
                        "No subscription exists for this user."));
        subscription.cancel();
        return response(subscription);
    }

    private void assertMockPurchasesEnabled() {
        if (!mockPurchasesEnabled) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                    "Mock subscription purchases are disabled.");
        }
    }

    private User requireActiveUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED,
                        "Authenticated user no longer exists."));
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "User account is not active.");
        }
        return user;
    }

    private SubscriptionResponse response(Subscription subscription) {
        SubscriptionPlan effectivePlan = subscription.effectivePlan();
        EntitlementFeatures features = planLimitService.featuresFor(effectivePlan);
        return new SubscriptionResponse(
                effectivePlan,
                subscription.getStatus(),
                subscription.getProvider(),
                subscription.getCurrentPeriodEnd(),
                features);
    }

    private SubscriptionResponse freeResponse() {
        return new SubscriptionResponse(
                SubscriptionPlan.FREE,
                SubscriptionStatus.ACTIVE,
                null,
                null,
                planLimitService.featuresFor(SubscriptionPlan.FREE));
    }

    private String mockProviderSubscriptionId(UUID userId, MockPurchaseRequest request) {
        String source = userId + ":" + request.plan() + ":" + request.provider() + ":" + request.receiptToken();
        return "mock-" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }
}
