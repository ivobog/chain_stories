package com.chainreaction.game.api;

import java.util.UUID;

import com.chainreaction.game.domain.StorySegment;

public record StorySegmentResponse(
        UUID segmentId,
        int sequenceNumber,
        Integer turnNumber,
        UUID authorUserId,
        String content) {

    public static StorySegmentResponse from(StorySegment segment) {
        return new StorySegmentResponse(
                segment.getId(),
                segment.getSequenceNumber(),
                segment.getTurnNumber(),
                segment.getAuthorUserId(),
                segment.getContent());
    }
}
