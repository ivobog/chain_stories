package com.chainreaction.game.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BotTurnListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(BotTurnListener.class);

    private final BotTurnService botTurnService;

    public BotTurnListener(BotTurnService botTurnService) {
        this.botTurnService = botTurnService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTurnStarted(TurnStartedInternalEvent event) {
        try {
            botTurnService.playIfBotTurn(event.gameId(), event.turnId());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "bot_turn_processing_failed gameId={} turnId={} reason={}",
                    event.gameId(),
                    event.turnId(),
                    exception.getMessage(),
                    exception);
        }
    }
}
