package com.chainreaction.game.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chainreaction.user.domain.User;
import com.chainreaction.user.domain.UserAccountType;
import com.chainreaction.user.domain.UserProfile;
import com.chainreaction.user.repository.UserProfileRepository;
import com.chainreaction.user.repository.UserRepository;

@Service
public class BotPlayerService {

    static final String STORY_BOT_EMAIL = "storybot@system.local";
    static final String STORY_BOT_DISPLAY_NAME = "StoryBot";

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;

    public BotPlayerService(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User getOrCreateStoryBot() {
        return userRepository.findByEmailIgnoreCase(STORY_BOT_EMAIL)
                .map(this::validateAndReturnExistingBot)
                .orElseGet(this::createStoryBot);
    }

    private User validateAndReturnExistingBot(User user) {
        if (!user.isBot()) {
            throw new IllegalStateException("Reserved StoryBot email belongs to a non-bot user.");
        }
        ensureProfileExists(user);
        return user;
    }

    private User createStoryBot() {
        User bot = new User(STORY_BOT_EMAIL, passwordEncoder.encode("bot-" + UUID.randomUUID()));
        bot.updateAccountType(UserAccountType.BOT);
        User savedBot = userRepository.save(bot);
        userProfileRepository.save(new UserProfile(savedBot, STORY_BOT_DISPLAY_NAME));
        return savedBot;
    }

    private void ensureProfileExists(User user) {
        if (userProfileRepository.findByUserId(user.getId()).isEmpty()) {
            userProfileRepository.save(new UserProfile(user, STORY_BOT_DISPLAY_NAME));
        }
    }
}
