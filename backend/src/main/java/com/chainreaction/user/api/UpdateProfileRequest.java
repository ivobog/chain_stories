package com.chainreaction.user.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(min = 2, max = 80) String displayName,
        @Size(max = 500) String avatarUrl,
        @Size(max = 64) String favoriteStyle) {
}
