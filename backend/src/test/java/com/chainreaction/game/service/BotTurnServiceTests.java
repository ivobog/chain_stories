package com.chainreaction.game.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.chainreaction.ai.WordSuggestionResult;
import com.chainreaction.ai.WordSuggestionService;
import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;
import com.chainreaction.game.domain.Game;
import com.chainreaction.game.domain.GameStatus;
import com.chainreaction.game.domain.GameTurn;
import com.chainreaction.game.domain.GameTurnStatus;
import com.chainreaction.game.repository.GameRepository;
import com.chainreaction.game.repository.GameTurnRepository;
import com.chainreaction.room.domain.Room;
import com.chainreaction.room.domain.RoomStatus;
import com.chainreaction.room.domain.SafetyMode;
import com.chainreaction.room.domain.WritingStyle;
import com.chainreaction.user.domain.User;
import com.chainreaction.word.WordRegistryService;

@ExtendWith(MockitoExtension.class)
class BotTurnServiceTests {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameTurnRepository gameTurnRepository;

    @Mock
    private WordSuggestionService wordSuggestionService;

    @Mock
    private WordRegistryService wordRegistryService;

    @Mock
    private GameService gameService;

    @Mock
    private TransactionTemplate transactionTemplate;

    private BotTurnService botTurnService;

    @BeforeEach
    void setUp() {
        botTurnService = new BotTurnService(
                gameRepository,
                gameTurnRepository,
                wordSuggestionService,
                wordRegistryService,
                gameService,
                transactionTemplate,
                0,
                3);
        org.mockito.Mockito.doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = (TransactionCallback<Object>) invocation.getArgument(0);
            return callback.doInTransaction(null);
        }).when(transactionTemplate).execute(any());
    }

    @Test
    void playIfBotTurnReturnsWhenGameIsNoLongerActive() {
        UUID gameId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        Game game = mock(Game.class);

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(game.getStatus()).thenReturn(GameStatus.VOTING);

        botTurnService.playIfBotTurn(gameId, turnId);

        verify(gameRepository).findById(gameId);
        verifyNoInteractions(gameTurnRepository, wordSuggestionService, wordRegistryService, gameService);
    }

    @Test
    void playIfBotTurnReturnsWhenRoomHasBeenClosed() {
        UUID gameId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        Game game = mock(Game.class);
        Room room = mock(Room.class);

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(game.getStatus()).thenReturn(GameStatus.ACTIVE);
        when(game.getRoom()).thenReturn(room);
        when(room.getStatus()).thenReturn(RoomStatus.CLOSED);

        botTurnService.playIfBotTurn(gameId, turnId);

        verify(gameRepository).findById(gameId);
        verifyNoInteractions(gameTurnRepository, wordSuggestionService, wordRegistryService, gameService);
    }

    @Test
    void playIfBotTurnReturnsWhenTurnIsNoLongerActive() {
        UUID gameId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        Game game = mock(Game.class);
        Room room = mock(Room.class);
        GameTurn turn = mock(GameTurn.class);

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(game.getId()).thenReturn(gameId);
        when(game.getStatus()).thenReturn(GameStatus.ACTIVE);
        when(game.getRoom()).thenReturn(room);
        when(room.getStatus()).thenReturn(RoomStatus.ACTIVE);
        when(gameTurnRepository.findByIdForUpdate(turnId)).thenReturn(Optional.of(turn));
        when(turn.getGame()).thenReturn(game);
        when(turn.getStatus()).thenReturn(GameTurnStatus.SUBMITTED);

        botTurnService.playIfBotTurn(gameId, turnId);

        verify(gameTurnRepository).findByIdForUpdate(turnId);
        verifyNoInteractions(wordSuggestionService, wordRegistryService, gameService);
    }

    @Test
    void playIfBotTurnSubmitsSuggestedWordForActiveBotTurn() {
        UUID gameId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        UUID botUserId = UUID.randomUUID();
        Game game = mock(Game.class);
        Room room = mock(Room.class);
        GameTurn turn = mock(GameTurn.class);
        User botUser = mock(User.class);

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(game.getId()).thenReturn(gameId);
        when(game.getStatus()).thenReturn(GameStatus.ACTIVE);
        when(game.getRoom()).thenReturn(room);
        when(room.getStatus()).thenReturn(RoomStatus.ACTIVE);
        when(room.getWritingStyle()).thenReturn(WritingStyle.FUNNY);
        when(room.getLanguage()).thenReturn("en");
        when(room.getSafetyMode()).thenReturn(SafetyMode.TEEN);
        when(gameTurnRepository.findByIdForUpdate(turnId)).thenReturn(Optional.of(turn));
        when(turn.getGame()).thenReturn(game);
        when(turn.getStatus()).thenReturn(GameTurnStatus.ACTIVE);
        when(turn.getPlayer()).thenReturn(botUser);
        when(botUser.isBot()).thenReturn(true);
        when(botUser.getId()).thenReturn(botUserId);
        when(gameService.fullStoryForGame(gameId)).thenReturn("A winding story.");
        when(wordRegistryService.acceptedWordsForGame(gameId)).thenReturn(List.of("dragon"));
        when(wordSuggestionService.suggest(any())).thenReturn(new WordSuggestionResult("moon", "moon", "TEEN"));

        botTurnService.playIfBotTurn(gameId, turnId);

        verify(wordSuggestionService).suggest(any());
        verify(gameService).submitBotWord(botUserId, gameId, turnId, "moon");
        verify(gameService).fullStoryForGame(gameId);
        verify(wordRegistryService).acceptedWordsForGame(gameId);
    }

    @Test
    void playIfBotTurnReturnsWhenCurrentTurnBelongsToHuman() {
        UUID gameId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        Game game = mock(Game.class);
        Room room = mock(Room.class);
        GameTurn turn = mock(GameTurn.class);
        User humanUser = mock(User.class);

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(game.getId()).thenReturn(gameId);
        when(game.getStatus()).thenReturn(GameStatus.ACTIVE);
        when(game.getRoom()).thenReturn(room);
        when(room.getStatus()).thenReturn(RoomStatus.ACTIVE);
        when(gameTurnRepository.findByIdForUpdate(turnId)).thenReturn(Optional.of(turn));
        when(turn.getGame()).thenReturn(game);
        when(turn.getStatus()).thenReturn(GameTurnStatus.ACTIVE);
        when(turn.getPlayer()).thenReturn(humanUser);
        when(humanUser.isBot()).thenReturn(false);

        botTurnService.playIfBotTurn(gameId, turnId);

        verify(gameTurnRepository).findByIdForUpdate(turnId);
        verify(wordSuggestionService, never()).suggest(any());
        verify(gameService, never()).submitBotWord(any(), any(), any(), any());
        verifyNoInteractions(wordRegistryService);
    }

    @Test
    void playIfBotTurnRetriesValidationFailuresWithNewSuggestion() {
        UUID gameId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        UUID botUserId = UUID.randomUUID();
        Game game = mock(Game.class);
        Room room = mock(Room.class);
        GameTurn turn = mock(GameTurn.class);
        User botUser = mock(User.class);

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(game.getId()).thenReturn(gameId);
        when(game.getStatus()).thenReturn(GameStatus.ACTIVE);
        when(game.getRoom()).thenReturn(room);
        when(room.getStatus()).thenReturn(RoomStatus.ACTIVE);
        when(room.getWritingStyle()).thenReturn(WritingStyle.FUNNY);
        when(room.getLanguage()).thenReturn("en");
        when(room.getSafetyMode()).thenReturn(SafetyMode.TEEN);
        when(gameTurnRepository.findByIdForUpdate(turnId)).thenReturn(Optional.of(turn));
        when(turn.getGame()).thenReturn(game);
        when(turn.getStatus()).thenReturn(GameTurnStatus.ACTIVE);
        when(turn.getPlayer()).thenReturn(botUser);
        when(botUser.isBot()).thenReturn(true);
        when(botUser.getId()).thenReturn(botUserId);
        when(gameService.fullStoryForGame(gameId)).thenReturn("A winding story.");
        when(wordRegistryService.acceptedWordsForGame(gameId)).thenReturn(List.of("dragon"));
        when(wordSuggestionService.suggest(any()))
                .thenReturn(new WordSuggestionResult("moon", "moon", "TEEN"))
                .thenReturn(new WordSuggestionResult("river", "river", "TEEN"));
        when(gameService.submitBotWord(botUserId, gameId, turnId, "moon"))
                .thenThrow(new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Blocked"));
        when(gameService.submitBotWord(botUserId, gameId, turnId, "river"))
                .thenReturn(null);

        botTurnService.playIfBotTurn(gameId, turnId);

        verify(wordSuggestionService, org.mockito.Mockito.times(2)).suggest(any());
        verify(gameService).submitBotWord(botUserId, gameId, turnId, "moon");
        verify(gameService).submitBotWord(botUserId, gameId, turnId, "river");
    }

    @Test
    void playIfBotTurnStopsAfterConfiguredRetryLimit() {
        UUID gameId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        UUID botUserId = UUID.randomUUID();
        Game game = mock(Game.class);
        Room room = mock(Room.class);
        GameTurn turn = mock(GameTurn.class);
        User botUser = mock(User.class);
        BotTurnService limitedRetryService = new BotTurnService(
                gameRepository,
                gameTurnRepository,
                wordSuggestionService,
                wordRegistryService,
                gameService,
                transactionTemplate,
                0,
                2);

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(game.getId()).thenReturn(gameId);
        when(game.getStatus()).thenReturn(GameStatus.ACTIVE);
        when(game.getRoom()).thenReturn(room);
        when(room.getStatus()).thenReturn(RoomStatus.ACTIVE);
        when(room.getWritingStyle()).thenReturn(WritingStyle.FUNNY);
        when(room.getLanguage()).thenReturn("en");
        when(room.getSafetyMode()).thenReturn(SafetyMode.TEEN);
        when(gameTurnRepository.findByIdForUpdate(turnId)).thenReturn(Optional.of(turn));
        when(turn.getGame()).thenReturn(game);
        when(turn.getStatus()).thenReturn(GameTurnStatus.ACTIVE);
        when(turn.getPlayer()).thenReturn(botUser);
        when(botUser.isBot()).thenReturn(true);
        when(botUser.getId()).thenReturn(botUserId);
        when(gameService.fullStoryForGame(gameId)).thenReturn("A winding story.");
        when(wordRegistryService.acceptedWordsForGame(gameId)).thenReturn(List.of("dragon"));
        when(wordSuggestionService.suggest(any())).thenReturn(new WordSuggestionResult("moon", "moon", "TEEN"));
        when(gameService.submitBotWord(botUserId, gameId, turnId, "moon"))
                .thenThrow(new ApiException(
                        ErrorCode.AI_GENERATION_FAILED,
                        HttpStatus.BAD_GATEWAY,
                        "Story generation failed. Please try again."));

        limitedRetryService.playIfBotTurn(gameId, turnId);

        verify(wordSuggestionService, org.mockito.Mockito.times(2)).suggest(any());
        verify(gameService, org.mockito.Mockito.times(2)).submitBotWord(botUserId, gameId, turnId, "moon");
    }

    @Test
    void playIfBotTurnIgnoresDuplicateProcessingAfterFirstSubmissionSucceeds() {
        UUID gameId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        UUID botUserId = UUID.randomUUID();
        Game game = mock(Game.class);
        Room room = mock(Room.class);
        GameTurn turn = mock(GameTurn.class);
        User botUser = mock(User.class);

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(game.getId()).thenReturn(gameId);
        when(game.getStatus()).thenReturn(GameStatus.ACTIVE);
        when(game.getRoom()).thenReturn(room);
        when(room.getStatus()).thenReturn(RoomStatus.ACTIVE);
        when(room.getWritingStyle()).thenReturn(WritingStyle.FUNNY);
        when(room.getLanguage()).thenReturn("en");
        when(room.getSafetyMode()).thenReturn(SafetyMode.TEEN);
        when(gameTurnRepository.findByIdForUpdate(turnId)).thenReturn(Optional.of(turn));
        when(turn.getGame()).thenReturn(game);
        when(turn.getStatus()).thenReturn(GameTurnStatus.ACTIVE, GameTurnStatus.SUBMITTED);
        when(turn.getPlayer()).thenReturn(botUser);
        when(botUser.isBot()).thenReturn(true);
        when(botUser.getId()).thenReturn(botUserId);
        when(gameService.fullStoryForGame(gameId)).thenReturn("A winding story.");
        when(wordRegistryService.acceptedWordsForGame(gameId)).thenReturn(List.of("dragon"));
        when(wordSuggestionService.suggest(any())).thenReturn(new WordSuggestionResult("moon", "moon", "TEEN"));
        when(gameService.submitBotWord(botUserId, gameId, turnId, "moon")).thenReturn(null);

        botTurnService.playIfBotTurn(gameId, turnId);
        botTurnService.playIfBotTurn(gameId, turnId);

        verify(wordSuggestionService).suggest(any());
        verify(gameService).submitBotWord(botUserId, gameId, turnId, "moon");
        verify(gameTurnRepository, org.mockito.Mockito.times(2)).findByIdForUpdate(turnId);
    }
}
