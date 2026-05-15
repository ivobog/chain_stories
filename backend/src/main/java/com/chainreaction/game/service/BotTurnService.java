package com.chainreaction.game.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.chainreaction.ai.WordSuggestionRequest;
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
import com.chainreaction.room.domain.RoomStatus;
import com.chainreaction.word.WordRegistryService;

@Service
public class BotTurnService {

    private final GameRepository gameRepository;
    private final GameTurnRepository gameTurnRepository;
    private final WordSuggestionService wordSuggestionService;
    private final WordRegistryService wordRegistryService;
    private final GameService gameService;
    private final TransactionTemplate transactionTemplate;
    private final long autoSubmitDelayMs;

    public BotTurnService(
            GameRepository gameRepository,
            GameTurnRepository gameTurnRepository,
            WordSuggestionService wordSuggestionService,
            WordRegistryService wordRegistryService,
            GameService gameService,
            TransactionTemplate transactionTemplate,
            @Value("${app.bot.auto-submit-delay-ms:1200}") long autoSubmitDelayMs) {
        this.gameRepository = gameRepository;
        this.gameTurnRepository = gameTurnRepository;
        this.wordSuggestionService = wordSuggestionService;
        this.wordRegistryService = wordRegistryService;
        this.gameService = gameService;
        this.transactionTemplate = transactionTemplate;
        this.autoSubmitDelayMs = Math.max(0, autoSubmitDelayMs);
    }

    public void playIfBotTurn(UUID gameId, UUID turnId) {
        if (autoSubmitDelayMs > 0) {
            try {
                Thread.sleep(autoSubmitDelayMs);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        transactionTemplate.executeWithoutResult(status -> playIfBotTurnObserved(gameId, turnId));
    }

    private void playIfBotTurnObserved(UUID gameId, UUID turnId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ApiException(ErrorCode.GAME_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Game does not exist."));
        if (game.getStatus() != GameStatus.ACTIVE || game.getRoom().getStatus() != RoomStatus.ACTIVE) {
            return;
        }

        GameTurn turn = gameTurnRepository.findByIdForUpdate(turnId)
                .orElseThrow(() -> new ApiException(ErrorCode.TURN_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Turn does not exist."));
        if (!turn.getGame().getId().equals(gameId)) {
            return;
        }
        if (turn.getStatus() != GameTurnStatus.ACTIVE || !turn.getPlayer().isBot()) {
            return;
        }

        WordSuggestionResult suggestion = wordSuggestionService.suggest(new WordSuggestionRequest(
                game.getRoom().getWritingStyle(),
                game.getRoom().getLanguage(),
                game.getRoom().getSafetyMode(),
                gameService.fullStoryForGame(gameId),
                wordRegistryService.acceptedWordsForGame(gameId)));
        gameService.submitBotWord(turn.getPlayer().getId(), gameId, turnId, suggestion.word());
    }
}
