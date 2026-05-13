package com.chainreaction.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;
import com.chainreaction.user.domain.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;

import jakarta.annotation.PostConstruct;

@Service
public class JwtTokenService {

    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final String issuer;
    private final String rawSecret;
    private final byte[] secret;
    private final Duration accessTokenTtl;
    private final boolean allowInsecureDevSecret;

    public JwtTokenService(
            ObjectMapper objectMapper,
            @Value("${app.security.jwt.issuer}") String issuer,
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.allow-insecure-dev-secret}") boolean allowInsecureDevSecret,
            @Value("${app.security.jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes) {
        this.objectMapper = objectMapper;
        this.issuer = issuer;
        this.rawSecret = secret;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.allowInsecureDevSecret = allowInsecureDevSecret;
        this.accessTokenTtl = Duration.ofMinutes(accessTokenTtlMinutes);
    }

    @PostConstruct
    void validateConfiguration() {
        if (accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalStateException("JWT access token TTL must be positive.");
        }
        boolean placeholderSecret = "replace-with-local-development-secret".equals(rawSecret);
        boolean shortSecret = secret.length < 32;
        if (!allowInsecureDevSecret && (placeholderSecret || shortSecret)) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes and not use the dev placeholder.");
        }
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenTtl);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", issuer);
        payload.put("sub", user.getId().toString());
        payload.put("email", user.getEmail());
        payload.put("role", user.getRole().name());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        String encodedHeader = encodeJson(header);
        String encodedPayload = encodeJson(payload);
        String unsignedToken = encodedHeader + "." + encodedPayload;
        return unsignedToken + "." + sign(unsignedToken);
    }

    public JwtClaims validate(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw invalidToken();
        }

        String unsignedToken = parts[0] + "." + parts[1];
        String expectedSignature = sign(unsignedToken);
        if (!constantTimeEquals(expectedSignature, parts[2])) {
            throw invalidToken();
        }

        Map<String, Object> payload = decodePayload(parts[1]);
        if (!issuer.equals(payload.get("iss"))) {
            throw invalidToken();
        }

        Instant expiresAt = Instant.ofEpochSecond(asLong(payload.get("exp")));
        if (!expiresAt.isAfter(Instant.now())) {
            throw invalidToken();
        }

        return new JwtClaims(
                UUID.fromString((String) payload.get("sub")),
                (String) payload.get("email"),
                (String) payload.get("role"),
                expiresAt);
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encode JWT JSON.", exception);
        }
    }

    private Map<String, Object> decodePayload(String encodedPayload) {
        try {
            byte[] json = BASE64_URL_DECODER.decode(encodedPayload);
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw invalidToken();
        }
    }

    private String sign(String unsignedToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign JWT.", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        if (leftBytes.length != rightBytes.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < leftBytes.length; i++) {
            result |= leftBytes[i] ^ rightBytes[i];
        }
        return result == 0;
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw invalidToken();
    }

    private ApiException invalidToken() {
        return new ApiException(ErrorCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED, "Invalid or expired access token.");
    }
}
