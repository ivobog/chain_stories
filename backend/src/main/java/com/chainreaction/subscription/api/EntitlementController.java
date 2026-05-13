package com.chainreaction.subscription.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chainreaction.common.security.CurrentUserPrincipal;
import com.chainreaction.subscription.service.EntitlementService;
import com.chainreaction.subscription.service.SubscriptionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/me")
public class EntitlementController {

    private final EntitlementService entitlementService;
    private final SubscriptionService subscriptionService;

    public EntitlementController(EntitlementService entitlementService, SubscriptionService subscriptionService) {
        this.entitlementService = entitlementService;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/entitlements")
    public EntitlementsResponse getEntitlements(@AuthenticationPrincipal CurrentUserPrincipal principal) {
        return entitlementService.getEntitlements(principal.getUserId());
    }

    @GetMapping("/subscription")
    public SubscriptionResponse getSubscription(@AuthenticationPrincipal CurrentUserPrincipal principal) {
        return subscriptionService.getSubscription(principal.getUserId());
    }

    @PostMapping("/subscription/mock-purchase")
    public SubscriptionResponse mockPurchase(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @Valid @RequestBody MockPurchaseRequest request) {
        return subscriptionService.mockPurchase(principal.getUserId(), request);
    }

    @PostMapping("/subscription/cancel")
    public SubscriptionResponse cancelSubscription(@AuthenticationPrincipal CurrentUserPrincipal principal) {
        return subscriptionService.cancel(principal.getUserId());
    }
}
