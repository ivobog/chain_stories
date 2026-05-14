package com.chainreaction.auth.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chainreaction.auth.api.PasswordResetConfirmRequest;
import com.chainreaction.auth.api.PasswordResetRequest;
import com.chainreaction.auth.api.PasswordResetResponse;
import com.chainreaction.auth.domain.PasswordResetToken;
import com.chainreaction.auth.repository.PasswordResetTokenRepository;
import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;
import com.chainreaction.user.domain.User;
import com.chainreaction.user.repository.UserRepository;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenHashService tokenHashService;
    private final AuthEventService authEventService;
    private final RefreshTokenService refreshTokenService;
    private final AuthRateLimiter authRateLimiter;
    private final long passwordResetTokenTtlMinutes;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            TokenHashService tokenHashService,
            AuthEventService authEventService,
            RefreshTokenService refreshTokenService,
            AuthRateLimiter authRateLimiter,
            @Value("${app.security.jwt.password-reset-token-ttl-minutes}") long passwordResetTokenTtlMinutes) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenHashService = tokenHashService;
        this.authEventService = authEventService;
        this.refreshTokenService = refreshTokenService;
        this.authRateLimiter = authRateLimiter;
        this.passwordResetTokenTtlMinutes = passwordResetTokenTtlMinutes;
    }

    @Transactional
    public PasswordResetResponse requestReset(PasswordResetRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        authRateLimiter.checkPasswordResetRequestAllowed(normalizedEmail);
        Optional<User> user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(User::isActive);

        if (user.isEmpty()) {
            authEventService.record(null, normalizedEmail, "PASSWORD_RESET_REQUESTED", "IGNORED", "NO_ACTIVE_ACCOUNT");
            return accepted(null);
        }

        String plaintextToken = tokenHashService.newOpaqueToken();
        PasswordResetToken resetToken = new PasswordResetToken(
                user.get(),
                tokenHashService.hash(plaintextToken),
                Instant.now().plus(passwordResetTokenTtlMinutes, ChronoUnit.MINUTES));
        passwordResetTokenRepository.save(resetToken);
        authEventService.record(user.get(), normalizedEmail, "PASSWORD_RESET_REQUESTED", "SUCCESS", null);

        return accepted(plaintextToken);
    }

    @Transactional
    public void confirmReset(PasswordResetConfirmRequest request) {
        String tokenHash = tokenHashService.hash(request.resetToken());
        authRateLimiter.checkPasswordResetConfirmAllowed(tokenHash);
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(this::invalidResetToken);
        if (!resetToken.isUsable(Instant.now()) || !resetToken.getUser().isActive()) {
            throw invalidResetToken();
        }

        User user = resetToken.getUser();
        user.updatePasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAllForUser(user);
        resetToken.markUsed();
        authEventService.record(user, user.getEmail(), "PASSWORD_RESET_CONFIRMED", "SUCCESS", null);
    }

    private PasswordResetResponse accepted(String developmentResetToken) {
        return new PasswordResetResponse("ACCEPTED", developmentResetToken);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ApiException invalidResetToken() {
        return new ApiException(
                ErrorCode.INVALID_PASSWORD_RESET_TOKEN,
                HttpStatus.UNAUTHORIZED,
                "Password reset token is invalid, expired, or already used.");
    }
}
