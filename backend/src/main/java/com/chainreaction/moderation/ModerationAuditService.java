package com.chainreaction.moderation;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.chainreaction.observability.ApplicationMetrics;
import com.chainreaction.room.domain.SafetyMode;

@Service
public class ModerationAuditService {

    private final ModerationEventRepository moderationEventRepository;
    private final ApplicationMetrics applicationMetrics;

    public ModerationAuditService(
            ModerationEventRepository moderationEventRepository,
            ApplicationMetrics applicationMetrics) {
        this.moderationEventRepository = moderationEventRepository;
        this.applicationMetrics = applicationMetrics;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBlocked(
            UUID gameId,
            UUID roomId,
            UUID turnId,
            UUID playerUserId,
            ModerationEventSource source,
            SafetyMode safetyMode,
            String reason,
            String contentExcerpt) {
        moderationEventRepository.save(new ModerationEvent(
                gameId,
                roomId,
                turnId,
                playerUserId,
                source,
                ModerationEventOutcome.BLOCKED,
                safetyMode,
                reason,
                contentExcerpt));
        applicationMetrics.recordModerationBlock(source, safetyMode);
    }
}
