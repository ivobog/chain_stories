package com.chainreaction.vote;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface VoteResultRepository extends JpaRepository<VoteResult, UUID> {

    List<VoteResult> findAllByGameIdOrderByCategoryAscResultRankAsc(UUID gameId);

    @Modifying
    @Query("delete from VoteResult result where result.gameId = :gameId")
    void deleteAllByGameId(UUID gameId);
}
