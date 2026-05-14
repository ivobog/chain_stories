package com.chainreaction.vote;

public enum VoteCategory {
    FUNNIEST_WORD,
    BEST_SABOTAGE,
    WEIRDEST_TWIST,
    BEST_AI_SENTENCE,
    MVP_PLAYER;

    public boolean targetsPlayer() {
        return this == BEST_SABOTAGE || this == MVP_PLAYER;
    }
}
