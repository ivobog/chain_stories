package com.chainreaction.room.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chainreaction.room.domain.RoomParticipant;
import com.chainreaction.room.domain.RoomParticipantStatus;
import com.chainreaction.room.domain.RoomStatus;

public interface RoomParticipantRepository extends JpaRepository<RoomParticipant, UUID> {

    Optional<RoomParticipant> findByRoomIdAndUserId(UUID roomId, UUID userId);

    List<RoomParticipant> findAllByRoomIdOrderByJoinedAtAsc(UUID roomId);

    @Query("""
            select participant from RoomParticipant participant
            where participant.user.id = :userId
              and participant.status = :participantStatus
              and participant.room.status in :roomStatuses
            order by participant.joinedAt desc
            """)
    List<RoomParticipant> findActiveRoomsForUser(
            @Param("userId") UUID userId,
            @Param("participantStatus") RoomParticipantStatus participantStatus,
            @Param("roomStatuses") Collection<RoomStatus> roomStatuses);

    long countByRoomIdAndStatus(UUID roomId, RoomParticipantStatus status);
}
