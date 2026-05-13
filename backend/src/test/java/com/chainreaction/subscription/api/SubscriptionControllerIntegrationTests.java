package com.chainreaction.subscription.api;

import static org.hamcrest.Matchers.equalTo;
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

import com.chainreaction.auth.api.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class SubscriptionControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void mockPurchaseIsDisabledByDefault() throws Exception {
        AuthResponse user = register("disabled-purchase-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(post("/api/v1/me/subscription/mock-purchase")
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "plan", "PLUS",
                                "provider", "TEST-STORE",
                                "receiptToken", "receipt-" + UUID.randomUUID()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", equalTo("ACCESS_DENIED")));
    }

    private AuthResponse register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", "SecretPassword123!",
                                "displayName", "Subscriber"))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), AuthResponse.class);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
