package com.chainreaction.game.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chainreaction.game.domain.Story;

public interface StoryRepository extends JpaRepository<Story, UUID> {

    Optional<Story> findByGameId(UUID gameId);
}
