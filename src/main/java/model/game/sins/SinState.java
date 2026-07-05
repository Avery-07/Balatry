package model.game.sins;

import java.math.BigDecimal;

/**
 * Per-player, round-scoped state owned by the active sin, living beside the {@link model.game.player.Run} exactly
 * as {@link model.game.player.Afflictions} and {@link model.game.player.PlayerStats} do. Sin modifiers (same
 * package) write it; other systems read it. Everything here resets each round via {@link #beginRound()}.
 *
 * <p>Round-scoped fields reset via {@link #beginRound()}; ante-scoped fields (Wrath's stacking free-joker
 * grants, which survive round transitions but expire with the ante) reset via {@link #beginAnte()}. Writes are
 * package-private where only {@link SinModifier}s mutate; Wrath's grant/consume pair is public because its
 * writers live at the Match (destroy) and Shop (purchase) layers.
 */
public final class SinState {

    private BigDecimal prideMultiplier = BigDecimal.ONE;   // Pride: the score multiplier this player chose this round
    private boolean prideThresholdMet;                     // Pride: whether score >= target x multiplier
    private int wrathFreeJokers;                           // Wrath: pending free-joker grants (ante-scoped, stacking)

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

    // --- Wrath: destroy a joker, the next joker purchase is free; grants stack and expire with the ante ---

    /** Pending free-joker grants. */
    public int getWrathFreeJokers() { return wrathFreeJokers; }

    /** Banks one free-joker grant (called by Match when a joker is destroyed under Wrath). */
    public void grantWrathFreeJoker() { wrathFreeJokers++; }

    /** Spends one grant on a completed joker purchase (called by the Shop at purchase completion). */
    public void consumeWrathFreeJoker() {
        if (wrathFreeJokers <= 0) throw new IllegalStateException("no Wrath grant to consume");
        wrathFreeJokers--;
    }

    /** Resets round-scoped sin state; called by {@link model.game.player.Run} at the start of each round. */
    public void beginRound() {
        prideMultiplier = BigDecimal.ONE;
        prideThresholdMet = false;
    }

    /** Resets ante-scoped sin state (unspent Wrath grants do not survive the ante); called at each ante start. */
    public void beginAnte() {
        wrathFreeJokers = 0;
    }
}
