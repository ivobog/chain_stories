package com.chainreaction.game.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;
import com.chainreaction.game.api.GameResponse;
import com.chainreaction.game.api.PlayWithBotRequest;
import com.chainreaction.game.api.PlayWithBotResponse;
import com.chainreaction.room.api.RoomResponse;
import com.chainreaction.room.domain.Room;
import com.chainreaction.room.service.RoomService;
import com.chainreaction.user.domain.User;
import com.chainreaction.user.repository.UserRepository;

@Service
public class PlayWithBotService {

    private final UserRepository userRepository;
    private final BotPlayerService botPlayerService;
    private final RoomService roomService;
    private final GameService gameService;

    public PlayWithBotService(
            UserRepository userRepository,
            BotPlayerService botPlayerService,
            RoomService roomService,
            GameService gameService) {
        this.userRepository = userRepository;
        this.botPlayerService = botPlayerService;
        this.roomService = roomService;
        this.gameService = gameService;
    }

    @Transactional
    public PlayWithBotResponse createAndStart(UUID humanUserId, PlayWithBotRequest request) {
        User human = requireActiveHumanUser(humanUserId);
        User bot = botPlayerService.getOrCreateStoryBot();

        Room room = roomService.createPlayWithBotRoom(
                human,
                request.writingStyle(),
                request.language(),
                request.safetyMode(),
                request.turnLimit(),
                request.turnTimeoutSeconds());
        roomService.addBotParticipant(room, bot);

        GameResponse game = gameService.startGame(human.getId(), room.getId());
        RoomResponse roomResponse = roomService.getRoom(human.getId(), room.getId());
        return new PlayWithBotResponse(roomResponse, game);
    }

    private User requireActiveHumanUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED,
                        "Authenticated user no longer exists."));
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "User account is not active.");
        }
        if (!user.isHuman()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                    "Only human users can create a play-with-bot game.");
        }
        return user;
    }
}
