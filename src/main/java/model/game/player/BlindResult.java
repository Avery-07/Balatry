package model.game.player;

import java.math.BigDecimal;

/** The competition-facing outcome of one player's blind: whether it was cleared, the final score vs target, hands left, and money earned. */
public record BlindResult(RoundOutcome outcome, BigDecimal score, long target, int handsRemaining, int moneyEarned) {

    /** Whether the blind was beaten. */
    public boolean cleared() { return outcome == RoundOutcome.WON; }
}