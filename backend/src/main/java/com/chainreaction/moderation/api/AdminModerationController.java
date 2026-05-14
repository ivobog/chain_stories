package com.chainreaction.moderation.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chainreaction.moderation.ModerationEventRepository;

@RestController
@RequestMapping("/api/v1/admin/moderation/events")
public class AdminModerationController {

    private final ModerationEventRepository moderationEventRepository;

    public AdminModerationController(ModerationEventRepository moderationEventRepository) {
        this.moderationEventRepository = moderationEventRepository;
    }

    @GetMapping
    public List<ModerationEventResponse> listRecentEvents() {
        return moderationEventRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(ModerationEventResponse::from)
                .toList();
    }
}
