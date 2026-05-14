package com.chainreaction.ai;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WordSuggestionEventRepository extends JpaRepository<WordSuggestionEvent, UUID> {

    List<WordSuggestionEvent> findAllByGameIdOrderByCreatedAtAsc(UUID gameId);
}
