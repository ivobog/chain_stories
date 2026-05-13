package com.chainreaction.user.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chainreaction.auth.service.AuthEventService;
import com.chainreaction.auth.service.RefreshTokenService;
import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;
import com.chainreaction.user.api.MeResponse;
import com.chainreaction.user.api.UpdateProfileRequest;
import com.chainreaction.user.domain.User;
import com.chainreaction.user.domain.UserProfile;
import com.chainreaction.user.repository.UserProfileRepository;
import com.chainreaction.user.repository.UserRepository;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuthEventService authEventService;

    public UserProfileService(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            RefreshTokenService refreshTokenService,
            AuthEventService authEventService) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.refreshTokenService = refreshTokenService;
        this.authEventService = authEventService;
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(UUID userId) {
        User user = requireUser(userId);
        UserProfile profile = requireProfile(userId);
        return MeResponse.from(user, profile);
    }

    @Transactional
    public MeResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = requireUser(userId);
        UserProfile profile = requireProfile(userId);
        profile.update(
                request.displayName().trim(),
                blankToNull(request.avatarUrl()),
                blankToNull(request.favoriteStyle()));
        return MeResponse.from(user, profile);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = requireUser(userId);
        String originalEmail = user.getEmail();
        UserProfile profile = requireProfile(userId);
        profile.anonymize();
        user.markDeleted();
        refreshTokenService.revokeAllForUser(user);
        authEventService.record(user, originalEmail, "ACCOUNT_DELETED", "SUCCESS", null);
    }

    private User requireUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED,
                        "Authenticated user no longer exists."));
        return user;
    }

    private UserProfile requireProfile(UUID userId) {
        return userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                        "User profile is missing."));
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
