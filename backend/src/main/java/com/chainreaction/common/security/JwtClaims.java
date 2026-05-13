package com.chainreaction.common.security;

import java.time.Instant;
import java.util.UUID;

public record JwtClaims(UUID userId, String email, String role, Instant expiresAt) {
}
