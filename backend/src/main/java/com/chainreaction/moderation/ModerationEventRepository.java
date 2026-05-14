package com.chainreaction.moderation;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ModerationEventRepository extends JpaRepository<ModerationEvent, UUID> {

    List<ModerationEvent> findTop50ByOrderByCreatedAtDesc();

    List<ModerationEvent> findAllByGameIdOrderByCreatedAtAsc(UUID gameId);
}
