package com.chainreaction.game.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chainreaction.game.domain.GameTurn;

public interface GameTurnRepository extends JpaRepository<GameTurn, UUID> {

    Optional<GameTurn> findByGameIdAndTurnNumber(UUID gameId, int turnNumber);

    List<GameTurn> findAllByGameIdOrderByTurnNumberAsc(UUID gameId);
}
