package com.chainreaction.auth.service;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chainreaction.auth.api.AuthResponse;
import com.chainreaction.auth.api.LoginRequest;
import com.chainreaction.auth.api.LogoutRequest;
import com.chainreaction.auth.api.RefreshTokenRequest;
import com.chainreaction.auth.api.RegisterRequest;
import com.chainreaction.auth.domain.RefreshToken;
import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;
import com.chainreaction.common.security.JwtTokenService;
import com.chainreaction.user.domain.User;
import com.chainreaction.user.domain.UserProfile;
import com.chainreaction.user.domain.UserStatus;
import com.chainreaction.user.repository.UserProfileRepository;
import com.chainreaction.user.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final AuthEventService authEventService;

    public AuthService(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            AuthEventService authEventService) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.authEventService = authEventService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsNonDeletedByEmailIgnoreCase(normalizedEmail)) {
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL, HttpStatus.CONFLICT, "Email is already registered.");
        }

        User user = userRepository.save(new User(normalizedEmail, passwordEncoder.encode(request.password())));
        userProfileRepository.save(new UserProfile(user, request.displayName().trim()));
        authEventService.record(user, normalizedEmail, "REGISTERED", "SUCCESS", null);
        return authResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> {
                    authEventService.record(null, normalizedEmail, "LOGIN", "FAILURE", "INVALID_CREDENTIALS");
                    return invalidCredentials();
                });
        assertCanAuthenticate(user);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            authEventService.record(user, normalizedEmail, "LOGIN", "FAILURE", "INVALID_CREDENTIALS");
            throw invalidCredentials();
        }

        authEventService.record(user, normalizedEmail, "LOGIN", "SUCCESS", null);
        return authResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken oldRefreshToken = refreshTokenService.consume(request.refreshToken());
        User user = oldRefreshToken.getUser();
        assertCanAuthenticate(user);

        IssuedRefreshToken replacementRefreshToken = refreshTokenService.issue(user);
        refreshTokenService.revokeWithReplacement(oldRefreshToken, replacementRefreshToken.entity());
        authEventService.record(user, user.getEmail(), "TOKEN_REFRESHED", "SUCCESS", null);
        return new AuthResponse(
                user.getId(),
                jwtTokenService.createAccessToken(user),
                replacementRefreshToken.plaintextToken(),
                "Bearer");
    }

    @Transactional
    public void logout(LogoutRequest request) {
        User user = refreshTokenService.revoke(request.refreshToken());
        authEventService.record(user, user.getEmail(), "LOGOUT", "SUCCESS", null);
    }

    private AuthResponse authResponse(User user) {
        IssuedRefreshToken refreshToken = refreshTokenService.issue(user);
        return new AuthResponse(
                user.getId(),
                jwtTokenService.createAccessToken(user),
                refreshToken.plaintextToken(),
                "Bearer");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void assertCanAuthenticate(User user) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED, HttpStatus.FORBIDDEN, "Account is suspended.");
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new ApiException(ErrorCode.ACCOUNT_DELETED, HttpStatus.FORBIDDEN, "Account is deleted.");
        }
    }

    private ApiException invalidCredentials() {
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    }
}
