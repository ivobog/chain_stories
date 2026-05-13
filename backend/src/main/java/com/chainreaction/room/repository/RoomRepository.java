package com.chainreaction.room.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chainreaction.room.domain.Room;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    Optional<Room> findByRoomCodeIgnoreCase(String roomCode);

    boolean existsByRoomCodeIgnoreCase(String roomCode);
}
