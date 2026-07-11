package model.game.player;

import java.math.BigDecimal;

/** The competition-facing outcome of one player's blind: whether it was cleared, the final score vs target, the single best hand played (The Mirage/The Shave exclusions, and a natural display stat), hands left, and money earned. */
public record BlindResult(RoundOutcome outcome, BigDecimal score, BigDecimal bestHand,
                          long target, int handsRemaining, int moneyEarned) {

    /** Whether the blind was beaten. */
    public boolean cleared() { return outcome == RoundOutcome.WON; }

    /** This result with {@code score} replaced (boss adjustments at the barrier); everything else unchanged. */
    public BlindResult withScore(BigDecimal newScore) {
        return new BlindResult(outcome, newScore, bestHand, target, handsRemaining, moneyEarned);
    }
}
