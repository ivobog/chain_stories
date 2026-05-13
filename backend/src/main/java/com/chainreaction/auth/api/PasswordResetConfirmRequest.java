package com.chainreaction.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.chainreaction.common.validation.StrongPassword;

public record PasswordResetConfirmRequest(
        @NotBlank String resetToken,
        @StrongPassword @NotBlank @Size(min = 8, max = 128) String newPassword) {
}
