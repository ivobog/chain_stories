package com.chainreaction.game.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chainreaction.game.domain.Game;

public interface GameRepository extends JpaRepository<Game, UUID> {

    Optional<Game> findByRoomId(UUID roomId);

    boolean existsByRoomId(UUID roomId);
}
