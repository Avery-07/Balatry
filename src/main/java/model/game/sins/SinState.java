package model.game.sins;

import java.math.BigDecimal;

/**
 * Per-player, round-scoped state owned by the active sin, living beside the {@link model.game.player.Run} exactly
 * as {@link model.game.player.Afflictions} and {@link model.game.player.PlayerStats} do. Sin modifiers (same
 * package) write it; other systems read it. Everything here resets each round via {@link #beginRound()}.
 *
 * <p>Today it carries only Pride's fields. As more sins gain per-player state, add fields here rather than a
 * generic bag, so each datum stays typed and discoverable. Writes are package-private (only {@link SinModifier}s
 * mutate); reads are public (e.g. the future points award reads {@link #pridePointMultiplier()}).
 */
public final class SinState {

    private BigDecimal prideMultiplier = BigDecimal.ONE;   // Pride: the score multiplier this player chose this round
    private boolean prideThresholdMet;                     // Pride: whether score >= target x multiplier

    /** The Pride multiplier this player chose this round (x1 by default / no gamble). */
    public BigDecimal getPrideMultiplier() { return prideMultiplier; }

    /** Whether this player's round score reached target x {@link #getPrideMultiplier()}. */
    public boolean isPrideThresholdMet()   { return prideThresholdMet; }

    /**
     * The factor the points award should apply to this player's round points: the chosen multiplier if the Pride
     * threshold was met, otherwise 1 (no effect). Read by the points system once it exists.
     */
    public BigDecimal pridePointMultiplier() { return prideThresholdMet ? prideMultiplier : BigDecimal.ONE; }

    void setPrideMultiplier(BigDecimal m)  { prideMultiplier = m; }
    void setPrideThresholdMet(boolean met) { prideThresholdMet = met; }

    /** Resets round-scoped sin state; called by {@link model.game.player.Run} at the start of each round. */
    public void beginRound() {
        prideMultiplier = BigDecimal.ONE;
        prideThresholdMet = false;
    }
}
