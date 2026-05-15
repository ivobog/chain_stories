package com.chainreaction.game.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chainreaction.game.domain.GameTurn;

import jakarta.persistence.LockModeType;

public interface GameTurnRepository extends JpaRepository<GameTurn, UUID> {

    Optional<GameTurn> findByGameIdAndTurnNumber(UUID gameId, int turnNumber);

    List<GameTurn> findAllByGameIdOrderByTurnNumberAsc(UUID gameId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select turn from GameTurn turn where turn.id = :turnId")
    Optional<GameTurn> findByIdForUpdate(@Param("turnId") UUID turnId);
}
