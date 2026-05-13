package com.chainreaction.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.chainreaction.auth.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update RefreshToken token
            set token.revokedAt = CURRENT_TIMESTAMP
            where token.user.id = :userId
              and token.revokedAt is null
            """)
    int revokeAllActiveByUserId(UUID userId);
}
