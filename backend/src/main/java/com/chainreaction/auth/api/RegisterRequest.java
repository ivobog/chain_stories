package com.chainreaction.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.chainreaction.common.validation.StrongPassword;

public record RegisterRequest(
        @Email @NotBlank @Size(max = 320) String email,
        @StrongPassword @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(min = 2, max = 80) String displayName) {
}
