package com.chainreaction.user.api;

import java.util.UUID;

import com.chainreaction.user.domain.User;
import com.chainreaction.user.domain.UserProfile;

public record MeResponse(
        UUID userId,
        String email,
        String displayName,
        String avatarUrl,
        String favoriteStyle,
        String status,
        String role) {

    public static MeResponse from(User user, UserProfile profile) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                profile.getDisplayName(),
                profile.getAvatarUrl(),
                profile.getFavoriteStyle(),
                user.getStatus().name(),
                user.getRole().name());
    }
}
