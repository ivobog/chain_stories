package com.chainreaction.subscription.api;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.chainreaction.auth.api.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "app.subscriptions.mock-purchases-enabled=true")
@AutoConfigureMockMvc
class SubscriptionMockPurchaseIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void defaultEntitlementsAndSubscriptionAreFree() throws Exception {
        AuthResponse user = register("entitlements-" + UUID.randomUUID() + "@example.com", "Entitlements");

        mockMvc.perform(get("/api/v1/me/entitlements")
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan", equalTo("FREE")))
                .andExpect(jsonPath("$.features.maxPlayersPerRoom", equalTo(2)))
                .andExpect(jsonPath("$.features.canShareStories", equalTo(false)));

        mockMvc.perform(get("/api/v1/me/subscription")
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan", equalTo("FREE")))
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.provider").doesNotExist())
                .andExpect(jsonPath("$.currentPeriodEnd").doesNotExist())
                .andExpect(jsonPath("$.features.maxPlayersPerRoom", equalTo(2)));
    }

    @Test
    void mockPurchaseUpgradesEntitlementsAndAllowsPaidRoomSize() throws Exception {
        AuthResponse user = register("purchase-plus-" + UUID.randomUUID() + "@example.com", "Buyer");

        mockPurchase(user.accessToken(), "PLUS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan", equalTo("PLUS")))
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.provider", equalTo("test-store")))
                .andExpect(jsonPath("$.features.maxPlayersPerRoom", equalTo(8)))
                .andExpect(jsonPath("$.features.canShareStories", equalTo(true)));

        mockMvc.perform(get("/api/v1/me/entitlements")
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan", equalTo("PLUS")))
                .andExpect(jsonPath("$.features.maxPlayersPerRoom", equalTo(8)));

        mockMvc.perform(get("/api/v1/me/subscription")
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan", equalTo("PLUS")))
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.provider", equalTo("test-store")))
                .andExpect(jsonPath("$.currentPeriodEnd").exists())
                .andExpect(jsonPath("$.features.maxPlayersPerRoom", equalTo(8)));

        createRoom(user.accessToken(), 8)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.maxPlayers", equalTo(8)));
    }

    @Test
    void cancellingMockSubscriptionFallsBackToFreeEntitlements() throws Exception {
        AuthResponse user = register("purchase-cancel-" + UUID.randomUUID() + "@example.com", "Buyer");

        mockPurchase(user.accessToken(), "CREATOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan", equalTo("CREATOR")))
                .andExpect(jsonPath("$.features.maxPlayersPerRoom", equalTo(20)));

        mockMvc.perform(post("/api/v1/me/subscription/cancel")
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan", equalTo("FREE")))
                .andExpect(jsonPath("$.status", equalTo("CANCELLED")))
                .andExpect(jsonPath("$.features.maxPlayersPerRoom", equalTo(2)));

        mockMvc.perform(get("/api/v1/me/subscription")
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan", equalTo("FREE")))
                .andExpect(jsonPath("$.status", equalTo("CANCELLED")))
                .andExpect(jsonPath("$.features.maxPlayersPerRoom", equalTo(2)));

        createRoom(user.accessToken(), 3)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", equalTo("ENTITLEMENT_REQUIRED")));
    }

    @Test
    void mockPurchaseRejectsFreeAndAdminPlans() throws Exception {
        AuthResponse user = register("purchase-invalid-" + UUID.randomUUID() + "@example.com", "Buyer");

        mockPurchase(user.accessToken(), "FREE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", equalTo("VALIDATION_FAILED")));

        mockPurchase(user.accessToken(), "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", equalTo("VALIDATION_FAILED")));
    }

    private ResultActions createRoom(String accessToken, int maxPlayers) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "writingStyle", "FUNNY",
                        "language", "en",
                        "safetyMode", "TEEN",
                        "maxPlayers", maxPlayers,
                        "turnLimit", 10,
                        "turnTimeoutSeconds", 30,
                        "visibility", "PRIVATE"))));
    }

    private ResultActions mockPurchase(String accessToken, String plan) throws Exception {
        return mockMvc.perform(post("/api/v1/me/subscription/mock-purchase")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "plan", plan,
                        "provider", "TEST-STORE",
                        "receiptToken", "receipt-" + UUID.randomUUID()))));
    }

    private AuthResponse register(String email, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", "SecretPassword123!",
                                "displayName", displayName))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), AuthResponse.class);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
