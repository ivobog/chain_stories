package com.chainreaction.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.chainreaction.user.repository.UserProfileRepository;

@SpringBootTest
class BotPlayerServiceTests {

    @Autowired
    private BotPlayerService botPlayerService;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Test
    void getOrCreateStoryBotCreatesBotUserAndReusesIt() {
        var firstBot = botPlayerService.getOrCreateStoryBot();
        var secondBot = botPlayerService.getOrCreateStoryBot();

        assertThat(firstBot.getId()).isEqualTo(secondBot.getId());
        assertThat(firstBot.isBot()).isTrue();
        assertThat(firstBot.isHuman()).isFalse();
        assertThat(userProfileRepository.findByUserId(firstBot.getId()))
                .isPresent()
                .get()
                .extracting(profile -> profile.getDisplayName())
                .isEqualTo(BotPlayerService.STORY_BOT_DISPLAY_NAME);
    }
}
