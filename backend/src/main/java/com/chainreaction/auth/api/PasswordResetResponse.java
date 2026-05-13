package com.chainreaction.auth.api;

public record PasswordResetResponse(String status, String developmentResetToken) {
}
