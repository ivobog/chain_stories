package com.chainreaction.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;
import com.chainreaction.game.api.PlayWithBotRequest;
import com.chainreaction.room.domain.ParticipantType;
import com.chainreaction.room.domain.RoomParticipantRole;
import com.chainreaction.room.domain.RoomStatus;
import com.chainreaction.room.domain.RoomVisibility;
import com.chainreaction.room.domain.SafetyMode;
import com.chainreaction.room.domain.WritingStyle;
import com.chainreaction.user.domain.User;
import com.chainreaction.user.domain.UserAccountType;
import com.chainreaction.user.domain.UserProfile;
import com.chainreaction.user.repository.UserProfileRepository;
import com.chainreaction.user.repository.UserRepository;

@SpringBootTest(properties = "app.bot.auto-submit-delay-ms=0")
class PlayWithBotServiceTests {

    @Autowired
    private PlayWithBotService playWithBotService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Test
    void createAndStartCreatesPrivateStartedBotGameForHumanUser() {
        User human = saveUser("play-bot-human-" + UUID.randomUUID() + "@example.com", "Host Human");

        var response = playWithBotService.createAndStart(human.getId(), request());

        assertThat(response.room().status()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(response.room().settings().visibility()).isEqualTo(RoomVisibility.PRIVATE);
        assertThat(response.room().settings().maxPlayers()).isEqualTo(2);
        assertThat(response.room().participants()).hasSize(2);
        assertThat(response.room().participants())
                .extracting(participant -> participant.participantType())
                .containsExactly(ParticipantType.HUMAN, ParticipantType.BOT);
        assertThat(response.room().participants())
                .extracting(participant -> participant.role())
                .containsExactly(RoomParticipantRole.HOST, RoomParticipantRole.PLAYER);
        assertThat(response.game().status().name()).isEqualTo("ACTIVE");
        assertThat(response.game().turnOrder()).hasSize(2);
        assertThat(response.game().storySegments()).hasSize(1);
        assertThat(response.game().roomId()).isEqualTo(response.room().roomId());
        assertThat(response.room().participants().get(1).displayName()).isEqualTo(BotPlayerService.STORY_BOT_DISPLAY_NAME);
        assertThat(response.room().participants().get(1).participantType()).isEqualTo(ParticipantType.BOT);
    }

    @Test
    void createAndStartRejectsBotCaller() {
        User bot = saveUser("play-bot-caller-" + UUID.randomUUID() + "@system.local", "Caller Bot");
        bot.updateAccountType(UserAccountType.BOT);
        bot = userRepository.save(bot);
        UUID botUserId = bot.getId();

        assertThatThrownBy(() -> playWithBotService.createAndStart(botUserId, request()))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    private User saveUser(String email, String displayName) {
        User user = userRepository.save(new User(email, "irrelevant-hash"));
        userProfileRepository.save(new UserProfile(user, displayName));
        return user;
    }

    private PlayWithBotRequest request() {
        return new PlayWithBotRequest(
                WritingStyle.FUNNY,
                "en",
                SafetyMode.TEEN,
                6,
                60);
    }
}
