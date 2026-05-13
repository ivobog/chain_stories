package com.chainreaction.ai;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiGenerationAttemptRepository extends JpaRepository<AiGenerationAttempt, UUID> {

    List<AiGenerationAttempt> findAllByGameIdOrderByCreatedAtAsc(UUID gameId);
}
