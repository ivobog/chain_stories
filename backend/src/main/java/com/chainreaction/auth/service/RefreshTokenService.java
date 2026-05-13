package com.chainreaction.auth.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.chainreaction.auth.domain.RefreshToken;
import com.chainreaction.auth.repository.RefreshTokenRepository;
import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;
import com.chainreaction.user.domain.User;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHashService tokenHashService;
    private final long refreshTokenTtlDays;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            TokenHashService tokenHashService,
            @Value("${app.security.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenHashService = tokenHashService;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    public IssuedRefreshToken issue(User user) {
        String plaintextToken = tokenHashService.newOpaqueToken();
        RefreshToken refreshToken = new RefreshToken(
                user,
                tokenHashService.hash(plaintextToken),
                Instant.now().plus(refreshTokenTtlDays, ChronoUnit.DAYS));
        return new IssuedRefreshToken(plaintextToken, refreshTokenRepository.save(refreshToken));
    }

    public RefreshToken consume(String plaintextToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHashService.hash(plaintextToken))
                .orElseThrow(this::invalidRefreshToken);
        if (!refreshToken.isUsable(Instant.now())) {
            throw invalidRefreshToken();
        }
        return refreshToken;
    }

    public void revokeWithReplacement(RefreshToken oldToken, RefreshToken replacementToken) {
        oldToken.revoke(replacementToken.getId());
        refreshTokenRepository.save(oldToken);
    }

    public User revoke(String plaintextToken) {
        RefreshToken refreshToken = consume(plaintextToken);
        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);
        return refreshToken.getUser();
    }

    public int revokeAllForUser(User user) {
        return refreshTokenRepository.revokeAllActiveByUserId(user.getId());
    }

    private ApiException invalidRefreshToken() {
        return new ApiException(
                ErrorCode.INVALID_REFRESH_TOKEN,
                HttpStatus.UNAUTHORIZED,
                "Refresh token is invalid, expired, or revoked.");
    }
}
