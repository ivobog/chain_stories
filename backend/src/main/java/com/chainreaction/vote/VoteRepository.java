package com.chainreaction.vote;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteRepository extends JpaRepository<Vote, UUID> {

    boolean existsByGameIdAndVoterUserIdAndCategory(UUID gameId, UUID voterUserId, VoteCategory category);

    List<Vote> findAllByGameIdOrderByCreatedAtAsc(UUID gameId);

    long countByGameId(UUID gameId);
}
