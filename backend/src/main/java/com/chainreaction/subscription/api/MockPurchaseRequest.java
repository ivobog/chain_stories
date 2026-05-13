package com.chainreaction.subscription.api;

import com.chainreaction.subscription.domain.SubscriptionPlan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MockPurchaseRequest(
        @NotNull SubscriptionPlan plan,
        @NotBlank @Size(max = 64) String provider,
        @NotBlank @Size(max = 512) String receiptToken) {
}
