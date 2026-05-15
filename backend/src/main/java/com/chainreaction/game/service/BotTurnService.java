package com.chainreaction.game.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(BotTurnService.class);

    private final GameRepository gameRepository;
    private final GameTurnRepository gameTurnRepository;
    private final WordSuggestionService wordSuggestionService;
    private final WordRegistryService wordRegistryService;
    private final GameService gameService;
    private final TransactionTemplate transactionTemplate;
    private final long autoSubmitDelayMs;
    private final int maxAutoSubmitAttempts;

    public BotTurnService(
            GameRepository gameRepository,
            GameTurnRepository gameTurnRepository,
            WordSuggestionService wordSuggestionService,
            WordRegistryService wordRegistryService,
            GameService gameService,
            TransactionTemplate transactionTemplate,
            @Value("${app.bot.auto-submit-delay-ms:1200}") long autoSubmitDelayMs,
            @Value("${app.bot.auto-submit-max-attempts:3}") int maxAutoSubmitAttempts) {
        this.gameRepository = gameRepository;
        this.gameTurnRepository = gameTurnRepository;
        this.wordSuggestionService = wordSuggestionService;
        this.wordRegistryService = wordRegistryService;
        this.gameService = gameService;
        this.transactionTemplate = transactionTemplate;
        this.autoSubmitDelayMs = Math.max(0, autoSubmitDelayMs);
        this.maxAutoSubmitAttempts = Math.max(1, maxAutoSubmitAttempts);
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

        for (int attempt = 1; attempt <= maxAutoSubmitAttempts; attempt++) {
            try {
                BotTurnOutcome outcome = transactionTemplate.execute(status -> playIfBotTurnObserved(gameId, turnId));
                if (outcome == null || outcome == BotTurnOutcome.SKIPPED || outcome == BotTurnOutcome.SUBMITTED) {
                    return;
                }
            } catch (ApiException exception) {
                if (!isRetryable(exception)) {
                    throw exception;
                }
                if (attempt >= maxAutoSubmitAttempts) {
                    LOGGER.warn(
                            "bot_turn_submission_exhausted gameId={} turnId={} attempts={} reason={}",
                            gameId,
                            turnId,
                            attempt,
                            exception.getMessage());
                    return;
                }
                LOGGER.info(
                        "bot_turn_submission_retrying gameId={} turnId={} attempt={} maxAttempts={} reason={}",
                        gameId,
                        turnId,
                        attempt,
                        maxAutoSubmitAttempts,
                        exception.getMessage());
            }
        }
    }

    private BotTurnOutcome playIfBotTurnObserved(UUID gameId, UUID turnId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> missingGame());
        if (game.getStatus() != GameStatus.ACTIVE || game.getRoom().getStatus() != RoomStatus.ACTIVE) {
            return BotTurnOutcome.SKIPPED;
        }

        GameTurn turn = gameTurnRepository.findByIdForUpdate(turnId)
                .orElseThrow(() -> missingTurn());
        if (!turn.getGame().getId().equals(gameId)) {
            return BotTurnOutcome.SKIPPED;
        }
        if (turn.getStatus() != GameTurnStatus.ACTIVE || !turn.getPlayer().isBot()) {
            return BotTurnOutcome.SKIPPED;
        }

        WordSuggestionResult suggestion = wordSuggestionService.suggest(new WordSuggestionRequest(
                game.getRoom().getWritingStyle(),
                game.getRoom().getLanguage(),
                game.getRoom().getSafetyMode(),
                gameService.fullStoryForGame(gameId),
                wordRegistryService.acceptedWordsForGame(gameId)));
        gameService.submitBotWord(turn.getPlayer().getId(), gameId, turnId, suggestion.word());
        return BotTurnOutcome.SUBMITTED;
    }

    private boolean isRetryable(ApiException exception) {
        return exception.getErrorCode() == ErrorCode.VALIDATION_FAILED
                || exception.getErrorCode() == ErrorCode.AI_GENERATION_FAILED;
    }

    private ApiException missingGame() {
        return new ApiException(ErrorCode.GAME_NOT_FOUND, org.springframework.http.HttpStatus.NOT_FOUND,
                "Game does not exist.");
    }

    private ApiException missingTurn() {
        return new ApiException(ErrorCode.TURN_NOT_FOUND, org.springframework.http.HttpStatus.NOT_FOUND,
                "Turn does not exist.");
    }

    private enum BotTurnOutcome {
        SKIPPED,
        SUBMITTED
    }
}
