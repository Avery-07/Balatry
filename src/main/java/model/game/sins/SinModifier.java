package model.game.sins;

import model.game.Match;
import model.game.player.BlindResult;
import model.game.player.Run;

/**
 * The per-ante sin's behavioural seam: a stateless strategy the {@link Match} dispatches to at fixed lifecycle
 * points. One {@link SinModifier} is active for the whole ante (the sin chosen by the {@link model.game.SinSelector}).
 *
 * <p><b>Why a strategy, not metadata.</b> Sins vary behaviourally far more than boss blinds (economy, shop,
 * scoring, settlement, cross-player), so declarative flags would not capture them; each sin expresses itself by
 * overriding the hooks it needs. Every hook defaults to a no-op, so a sin implements only what it touches and an
 * absent sin ({@link #NONE}) changes nothing.
 *
 * <p><b>Statelessness.</b> A modifier is a singleton shared by all seats, so it holds no per-player state. Where a
 * sin needs per-player, round-scoped accumulation (e.g. Lust's persistent mult), that state belongs beside the
 * {@link Run} — the same place {@link model.game.player.Afflictions} and {@link model.game.player.PlayerStats}
 * live — not on the modifier. That home is an open design decision and is intentionally not yet introduced here.
 *
 * <p><b>Scope of the current hooks.</b> Only the three decision-free, subsystem-free lifecycle points are present.
 * Hooks that require a synchronous player choice (Pride's modifier, Wrath's card), couple to the scoring engine
 * (Lust), the shop generator (Greed/Lust/Gluttony), the points award (Pride/Gluttony), or cross-player resolution
 * (Envy) are deliberately omitted until their shape is settled, so this seam can land without churn elsewhere.
 */
public interface SinModifier {

    /** Once per ante, table scope: set up any shared/cross-player state the sin needs (called before the deal). */
    default void onAnteBegin(Match match) { }

    /** Per seat, after its round is dealt: initialise any per-round sin state for this player. */
    default void onRoundBegin(Run run) { }

    /** Per seat, after its round is settled: read the {@code result} and apply any end-of-round sin effect. */
    default void onRoundSettled(Run run, BlindResult result) { }

    /** The inert sin: no ante is modified. Used as the default and whenever a sin has no model behaviour yet. */
    SinModifier NONE = new SinModifier() { };
}
