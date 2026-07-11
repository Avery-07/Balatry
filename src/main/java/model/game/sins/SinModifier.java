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
 * <p><b>Scope of the current hooks.</b> The three decision-free lifecycle points, plus {@link #configureShop}
 * (the shop-modifier pass: Greed/Lust/Gluttony's seam). Hooks that couple to the scoring engine (Lust's
 * per-hand-type mult), the points award (Gluttony's pool), or cross-player resolution (Envy) remain omitted
 * until their shape is settled, so this seam can grow without churn elsewhere.
 */
public interface SinModifier {

    /** Once per ante, table scope: set up any shared/cross-player state the sin needs (called before the deal). */
    default void onAnteBegin(Match match) { }

    /** Per seat, after its round is dealt: initialise any per-round sin state for this player. */
    default void onRoundBegin(Run run) { }

    /** Per seat, after its round is settled: read the {@code result} and apply any end-of-round sin effect. */
    default void onRoundSettled(Run run, BlindResult result) { }

    /**
     * Per seat, as its shop opens: mutate {@code setup} to shape this visit (pools, row sizes, pricing rules,
     * purchase limits). Runs before the run's pending NEXT_SHOP tags apply, so the sin sets the ante's ambient
     * shop environment and tags layer player-earned boosts on top. This is Greed/Lust/Gluttony's seam.
     */
    default void configureShop(Run run, model.game.shop.ShopSetup setup) { }

    /**
     * Per seat, after each consumable use — a tarot/planet/spectral from the consumable area, a relic cast,
     * or an eaten joker under Gluttony. Fired only for seated runs (headless runs have no table).
     */
    default void onConsumableUsed(Run run) { }

    /**
     * At the table, when an ante's boss settles — after results and points, before shops open (or the match
     * finishes). The moment for end-of-ante sin resolution, e.g. Gluttony's gauge payout.
     */
    default void onAnteSettled(Match match) { }

    /**
     * Per seat, after the engine scores a hand but before the score joins the round total: return the
     * (possibly transformed) hand score. Lust multiplies here; the returned value is what the round, the
     * best-hand tracker, the play result, and {@link #onHandScored} all see. Fired only for seated runs.
     */
    default java.math.BigDecimal adjustHandScore(Run run, model.game.scoring.HandType type,
                                                 java.math.BigDecimal handScore) { return handScore; }

    /**
     * Per seat, after each hand is fully scored and boss after-effects applied (Greed's chips-to-money ladder).
     * {@code handScore} is this hand's contribution (post-{@link #adjustHandScore}), not the round total.
     * Fired only for seated runs.
     */
    default void onHandScored(Run run, java.math.BigDecimal handScore) { }

    /**
     * Per seat, after a completed shop purchase from any row — card, pack, or voucher (Greed's claim
     * propagation). Fired only for seated runs, and never for a failed purchase.
     */
    default void onPurchase(Run buyer, model.cards.Card item) { }

    /**
     * Per seat, after its shop's card row rerolls, with the fresh contents in place (Greed re-debuffs claimed
     * items that reappear). Fired only for seated runs.
     */
    default void onShopRerolled(Run run, model.game.shop.Shop shop) { }

    /** How many copies of the blind's tag a skip grants (Sloth: 2). */
    default int tagsPerSkip() { return 1; }

    /** The inert sin: no ante is modified. Used as the default and whenever a sin has no model behaviour yet. */
    SinModifier NONE = new SinModifier() { };
}
