package com.chainreaction.auth.api;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerLoginRefreshAndMeFlow() throws Exception {
        String email = "phase1-" + UUID.randomUUID() + "@example.com";
        String password = "SecretPassword123!";
        String displayName = "Phase One";

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", password,
                                "displayName", displayName))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.userId", not(blankOrNullString())))
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.refreshToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.tokenType", equalTo("Bearer")))
                .andReturn();

        Map<String, Object> registerBody = responseBody(registerResult);
        String accessToken = (String) registerBody.get("accessToken");
        String refreshToken = (String) registerBody.get("refreshToken");

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", equalTo(email)))
                .andExpect(jsonPath("$.displayName", equalTo(displayName)))
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.role", equalTo("ROLE_USER")));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.refreshToken", not(blankOrNullString())));

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.refreshToken", not(blankOrNullString())))
                .andReturn();

        String rotatedRefreshToken = (String) responseBody(refreshResult).get("refreshToken");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", equalTo("INVALID_REFRESH_TOKEN")));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", rotatedRefreshToken))))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        AuthResponse auth = register("logout-" + UUID.randomUUID() + "@example.com", "Logout Test");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", auth.refreshToken()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", auth.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", equalTo("INVALID_REFRESH_TOKEN")));
    }

    @Test
    void duplicateRegistrationIsRejected() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.com";
        Map<String, String> request = Map.of(
                "email", email,
                "password", "SecretPassword123!",
                "displayName", "Duplicate");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("DUPLICATE_EMAIL")));
    }

    @Test
    void weakRegistrationPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "weak-" + UUID.randomUUID() + "@example.com",
                                "password", "password",
                                "displayName", "Weak Password"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", equalTo("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.fieldErrors[0].field", equalTo("password")));
    }

    @Test
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", equalTo("AUTH_REQUIRED")));
    }

    @Test
    void invalidBearerTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", equalTo("AUTH_REQUIRED")));
    }

    @Test
    void updateProfileAndDeleteAccount() throws Exception {
        String email = "profile-" + UUID.randomUUID() + "@example.com";
        AuthResponse auth = register(email, "Profile Test");
        String replacementPassword = "ReplacementPassword123!";

        mockMvc.perform(patch("/api/v1/me/profile")
                        .header("Authorization", "Bearer " + auth.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "displayName", "Updated Profile",
                                "avatarUrl", "https://example.com/avatar.png",
                                "favoriteStyle", "FUNNY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName", equalTo("Updated Profile")))
                .andExpect(jsonPath("$.avatarUrl", equalTo("https://example.com/avatar.png")))
                .andExpect(jsonPath("$.favoriteStyle", equalTo("FUNNY")));

        mockMvc.perform(delete("/api/v1/me")
                        .header("Authorization", "Bearer " + auth.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + auth.accessToken()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", auth.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", equalTo("INVALID_REFRESH_TOKEN")));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", replacementPassword,
                                "displayName", "Replacement Account"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", replacementPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())));
    }

    @Test
    void passwordResetChangesPasswordAndTokenIsSingleUse() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@example.com";
        AuthResponse auth = register(email, "Reset Test");

        MvcResult resetRequestResult = mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("ACCEPTED")))
                .andExpect(jsonPath("$.developmentResetToken", not(blankOrNullString())))
                .andReturn();

        String resetToken = (String) responseBody(resetRequestResult).get("developmentResetToken");
        String newPassword = "NewSecretPassword123!";

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "resetToken", resetToken,
                                "newPassword", newPassword))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "resetToken", resetToken,
                                "newPassword", "AnotherPassword123!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", equalTo("INVALID_PASSWORD_RESET_TOKEN")));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", "SecretPassword123!"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", auth.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", equalTo("INVALID_REFRESH_TOKEN")));
    }

    @Test
    void weakPasswordResetPasswordIsRejected() throws Exception {
        String email = "weak-reset-" + UUID.randomUUID() + "@example.com";
        register(email, "Weak Reset");

        MvcResult resetRequestResult = mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email))))
                .andExpect(status().isOk())
                .andReturn();

        String resetToken = (String) responseBody(resetRequestResult).get("developmentResetToken");

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "resetToken", resetToken,
                                "newPassword", "newpassword"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", equalTo("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.fieldErrors[0].field", equalTo("newPassword")));
    }

    @Test
    void passwordResetRequestDoesNotRevealUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "missing-" + UUID.randomUUID() + "@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("ACCEPTED")))
                .andExpect(jsonPath("$.developmentResetToken").doesNotExist());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private Map<String, Object> responseBody(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsByteArray(),
                new TypeReference<>() {
                });
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
}
