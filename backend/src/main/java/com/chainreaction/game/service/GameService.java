package com.chainreaction.game.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chainreaction.ai.StoryGenerationResult;
import com.chainreaction.ai.StoryGenerationService;
import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;
import com.chainreaction.game.api.GameResponse;
import com.chainreaction.game.api.GameTurnResponse;
import com.chainreaction.game.api.SubmitWordRequest;
import com.chainreaction.game.api.StorySegmentResponse;
import com.chainreaction.game.domain.Game;
import com.chainreaction.game.domain.GameStatus;
import com.chainreaction.game.domain.GameTurn;
import com.chainreaction.game.domain.GameTurnStatus;
import com.chainreaction.game.domain.Story;
import com.chainreaction.game.domain.StorySegment;
import com.chainreaction.game.repository.GameRepository;
import com.chainreaction.game.repository.GameTurnRepository;
import com.chainreaction.game.repository.StoryRepository;
import com.chainreaction.game.repository.StorySegmentRepository;
import com.chainreaction.realtime.api.RealtimeEventType;
import com.chainreaction.realtime.service.RealtimeEventPublisher;
import com.chainreaction.room.domain.Room;
import com.chainreaction.room.domain.RoomParticipant;
import com.chainreaction.room.domain.RoomParticipantStatus;
import com.chainreaction.room.domain.RoomStatus;
import com.chainreaction.room.repository.RoomParticipantRepository;
import com.chainreaction.room.repository.RoomRepository;
import com.chainreaction.word.WordRegistryService;

@Service
public class GameService {

    private static final int MIN_PLAYERS_TO_START = 2;
    private static final String OPENING_SEGMENT = "The story begins, waiting for the first word.";

    private final GameRepository gameRepository;
    private final GameTurnRepository gameTurnRepository;
    private final StoryRepository storyRepository;
    private final StorySegmentRepository storySegmentRepository;
    private final RoomRepository roomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final StoryGenerationService storyGenerationService;
    private final WordRegistryService wordRegistryService;

    public GameService(
            GameRepository gameRepository,
            GameTurnRepository gameTurnRepository,
            StoryRepository storyRepository,
            StorySegmentRepository storySegmentRepository,
            RoomRepository roomRepository,
            RoomParticipantRepository roomParticipantRepository,
            RealtimeEventPublisher realtimeEventPublisher,
            StoryGenerationService storyGenerationService,
            WordRegistryService wordRegistryService) {
        this.gameRepository = gameRepository;
        this.gameTurnRepository = gameTurnRepository;
        this.storyRepository = storyRepository;
        this.storySegmentRepository = storySegmentRepository;
        this.roomRepository = roomRepository;
        this.roomParticipantRepository = roomParticipantRepository;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.storyGenerationService = storyGenerationService;
        this.wordRegistryService = wordRegistryService;
    }

    @Transactional
    public GameResponse startGame(UUID userId, UUID roomId) {
        Room room = requireRoom(roomId);
        requireHost(room, userId);
        if (room.getStatus() != RoomStatus.LOBBY) {
            throw new ApiException(ErrorCode.GAME_ALREADY_STARTED, HttpStatus.CONFLICT,
                    "Room is not in the lobby.");
        }
        if (gameRepository.existsByRoomId(roomId)) {
            throw new ApiException(ErrorCode.GAME_ALREADY_STARTED, HttpStatus.CONFLICT,
                    "A game already exists for this room.");
        }

        List<RoomParticipant> participants = activeParticipants(roomId);
        if (participants.size() < MIN_PLAYERS_TO_START) {
            throw new ApiException(ErrorCode.GAME_NOT_READY, HttpStatus.CONFLICT,
                    "At least two active players are required to start a game.");
        }

        room.startGame();
        Game game = gameRepository.save(new Game(room));
        GameTurn firstTurn = gameTurnRepository.save(new GameTurn(
                game,
                participants.get(0).getUser(),
                1,
                game.getTurnTimeoutSeconds()));
        Story story = storyRepository.save(new Story(game));
        storySegmentRepository.save(new StorySegment(story, null, null, 1, OPENING_SEGMENT));

        GameResponse result = response(game, firstTurn, participants);
        publishGameEvent(game, RealtimeEventType.GAME_STARTED, result);
        publishGameEvent(game, RealtimeEventType.TURN_STARTED, result);
        return result;
    }

    @Transactional(readOnly = true)
    public GameResponse getGame(UUID userId, UUID gameId) {
        Game game = requireGame(gameId);
        requireActiveParticipant(game.getRoom().getId(), userId);
        GameTurn currentTurn = gameTurnRepository.findByGameIdAndTurnNumber(gameId, game.getCurrentTurnNumber())
                .orElseThrow(() -> new ApiException(ErrorCode.GAME_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Current turn does not exist."));
        return response(game, currentTurn, activeParticipants(game.getRoom().getId()));
    }

    @Transactional
    public GameResponse submitWord(UUID userId, UUID gameId, UUID turnId, SubmitWordRequest request) {
        Game game = requireActiveGame(gameId);
        requireActiveParticipant(game.getRoom().getId(), userId);
        GameTurn turn = requireCurrentActiveTurn(game, turnId);
        if (!turn.getPlayer().getId().equals(userId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                    "Only the current player can submit a word.");
        }

        Story story = storyRepository.findByGameId(gameId)
                .orElseThrow(() -> new ApiException(ErrorCode.GAME_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Story does not exist."));
        publishGameEvent(game, RealtimeEventType.AI_GENERATION_STARTED,
                response(game, turn, activeParticipants(game.getRoom().getId())));
        StoryGenerationResult generation = storyGenerationService.generate(
                game.getId(),
                turn.getId(),
                game.getRoom(),
                request.word(),
                fullStory(story.getId()));

        turn.submit();
        int sequenceNumber = (int) storySegmentRepository.countByStoryId(story.getId()) + 1;
        StorySegment storySegment = storySegmentRepository.save(new StorySegment(
                story,
                turn,
                turn.getPlayer(),
                sequenceNumber,
                generation.sentence()));
        wordRegistryService.recordAcceptedUsage(game, turn, storySegment, generation);

        GameResponse result = advanceAfterTurn(game, turn);
        publishGameEvent(game, RealtimeEventType.WORD_SUBMITTED, result);
        publishGameEvent(game, RealtimeEventType.STORY_SEGMENT_ADDED, result);
        publishAfterTurnAdvance(game, result);
        return result;
    }

    @Transactional
    public GameResponse skipExpiredTurn(UUID userId, UUID gameId, UUID turnId) {
        Game game = requireActiveGame(gameId);
        requireActiveParticipant(game.getRoom().getId(), userId);
        GameTurn turn = requireCurrentActiveTurn(game, turnId);
        if (turn.getExpiresAt().isAfter(Instant.now())) {
            throw new ApiException(ErrorCode.TURN_NOT_EXPIRED, HttpStatus.CONFLICT,
                    "Turn has not expired yet.");
        }

        turn.skip();
        GameResponse result = advanceAfterTurn(game, turn);
        publishGameEvent(game, RealtimeEventType.TURN_SKIPPED, result);
        publishAfterTurnAdvance(game, result);
        return result;
    }

    private GameResponse advanceAfterTurn(Game game, GameTurn turn) {
        List<RoomParticipant> participants = activeParticipants(game.getRoom().getId());
        GameTurn currentTurn = turn;
        if (turn.getTurnNumber() >= game.getTurnLimit()) {
            game.moveToVoting();
        } else {
            int nextTurnNumber = turn.getTurnNumber() + 1;
            currentTurn = gameTurnRepository.save(new GameTurn(
                    game,
                    nextPlayer(participants, nextTurnNumber),
                    nextTurnNumber,
                    game.getTurnTimeoutSeconds()));
            game.advanceToTurn(nextTurnNumber);
        }

        return response(game, currentTurn, participants);
    }

    private void publishAfterTurnAdvance(Game game, GameResponse result) {
        if (game.getStatus() == GameStatus.VOTING) {
            publishGameEvent(game, RealtimeEventType.VOTING_STARTED, result);
        } else {
            publishGameEvent(game, RealtimeEventType.TURN_STARTED, result);
        }
    }

    private void publishGameEvent(Game game, RealtimeEventType type, GameResponse payload) {
        realtimeEventPublisher.publishGameEvent(game.getRoom().getId(), game.getId(), type, payload);
    }

    private Game requireActiveGame(UUID gameId) {
        Game game = requireGame(gameId);
        if (game.getStatus() != GameStatus.ACTIVE) {
            throw new ApiException(ErrorCode.TURN_NOT_ACTIVE, HttpStatus.CONFLICT,
                    "Game is not accepting turn changes.");
        }
        return game;
    }

    private GameTurn requireCurrentActiveTurn(Game game, UUID turnId) {
        GameTurn turn = gameTurnRepository.findById(turnId)
                .orElseThrow(() -> new ApiException(ErrorCode.TURN_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Turn does not exist."));
        if (!turn.getGame().getId().equals(game.getId())) {
            throw new ApiException(ErrorCode.TURN_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Turn does not belong to this game.");
        }
        if (turn.getTurnNumber() != game.getCurrentTurnNumber() || turn.getStatus() != GameTurnStatus.ACTIVE) {
            throw new ApiException(ErrorCode.TURN_NOT_ACTIVE, HttpStatus.CONFLICT,
                    "Turn is not active.");
        }
        return turn;
    }

    private GameResponse response(Game game, GameTurn currentTurn, List<RoomParticipant> participants) {
        Story story = storyRepository.findByGameId(game.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.GAME_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Story does not exist."));
        List<UUID> turnOrder = participants.stream()
                .map(participant -> participant.getUser().getId())
                .toList();
        List<StorySegmentResponse> segments = storySegmentRepository.findAllByStoryIdOrderBySequenceNumberAsc(story.getId())
                .stream()
                .map(StorySegmentResponse::from)
                .toList();
        List<GameTurnResponse> turns = gameTurnRepository.findAllByGameIdOrderByTurnNumberAsc(game.getId())
                .stream()
                .map(GameTurnResponse::from)
                .toList();
        return GameResponse.from(game, GameTurnResponse.from(currentTurn), turnOrder, turns, segments);
    }

    private Room requireRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Room does not exist."));
    }

    private Game requireGame(UUID gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new ApiException(ErrorCode.GAME_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Game does not exist."));
    }

    private void requireHost(Room room, UUID userId) {
        if (!room.getHost().getId().equals(userId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Only the host can start a game.");
        }
    }

    private void requireActiveParticipant(UUID roomId, UUID userId) {
        RoomParticipant participant = roomParticipantRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                        "User is not a room participant."));
        if (participant.getStatus() != RoomParticipantStatus.JOINED) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                    "User is not an active room participant.");
        }
    }

    private List<RoomParticipant> activeParticipants(UUID roomId) {
        return roomParticipantRepository.findAllByRoomIdOrderByJoinedAtAsc(roomId).stream()
                .filter(participant -> participant.getStatus() == RoomParticipantStatus.JOINED)
                .toList();
    }

    private String fullStory(UUID storyId) {
        return storySegmentRepository.findAllByStoryIdOrderBySequenceNumberAsc(storyId)
                .stream()
                .map(StorySegment::getContent)
                .reduce((first, second) -> first + "\n\n" + second)
                .orElse("");
    }

    private com.chainreaction.user.domain.User nextPlayer(List<RoomParticipant> participants, int turnNumber) {
        int index = (turnNumber - 1) % participants.size();
        return participants.get(index).getUser();
    }
}
