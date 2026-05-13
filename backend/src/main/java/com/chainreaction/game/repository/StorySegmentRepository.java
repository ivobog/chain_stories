package com.chainreaction.game.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chainreaction.game.domain.StorySegment;

public interface StorySegmentRepository extends JpaRepository<StorySegment, UUID> {

    List<StorySegment> findAllByStoryIdOrderBySequenceNumberAsc(UUID storyId);

    long countByStoryId(UUID storyId);
}
