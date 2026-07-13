package model.game.sins;

import model.game.Match;
import model.game.player.BlindResult;
import model.game.player.Run;

/** The per-ante sin's behavioural seam: a stateless strategy the {@link Match} dispatches to at fixed lifecycle points. */
public interface SinModifier {

    /** Once per ante, table scope: set up any shared/cross-player state the sin needs (called before the deal). */
    default void onAnteBegin(Match match) { }

    /** Per seat, after its round is dealt: initialise any per-round sin state for this player. */
    default void onRoundBegin(Run run) { }

    /** Per seat, after its round is settled: read the {@code result} and apply any end-of-round sin effect. */
    default void onRoundSettled(Run run, BlindResult result) { }

    /** Per seat, as its shop opens: mutate {@code setup} to shape this visit (pools, row sizes, pricing rules, purchase limits). */
    default void configureShop(Run run, model.game.shop.ShopSetup setup) { }

    /** Per seat, after each consumable use — a tarot/planet/spectral from the consumable area, a relic cast, or an eaten joker under Gluttony. */
    default void onConsumableUsed(Run run) { }

    /** At the table, when an ante's boss settles — after results and points, before shops open (or the match finishes). */
    default void onAnteSettled(Match match) { }

    /** Per seat, after the engine scores a hand but before the score joins the round total: return the (possibly transformed) hand score. */
    default java.math.BigDecimal adjustHandScore(Run run, model.game.scoring.HandType type,
                                                 java.math.BigDecimal handScore) { return handScore; }

    /** Per seat, after each hand is fully scored and boss after-effects applied (Greed's chips-to-money ladder). */
    default void onHandScored(Run run, java.math.BigDecimal handScore) { }

    /** Per seat, after a completed shop purchase from any row, with the price actually paid (Greed's claims, Envy's log). */
    default void onPurchase(Run buyer, model.cards.Card item, int pricePaid) { }

    /** Per seat, as its shop closes at the end of the phase (Sloth's empty-visit spectral). */
    default void onShopClosed(Run run, model.game.shop.Shop shop) { }

    /** At the table, once all seats' shops have closed and before the sin can rotate (Pride's auction resolves). */
    default void onShopPhaseEnd(Match match) { }

    /** Per seat, after its shop's card row rerolls, with the fresh contents in place (Greed re-debuffs claimed items that reappear). */
    default void onShopRerolled(Run run, model.game.shop.Shop shop) { }

    /** How many copies of the blind's tag a skip grants (Sloth: 2). */
    default int tagsPerSkip() { return 1; }

    /** The inert sin: no ante is modified. Used as the default and whenever a sin has no model behaviour yet. */
    SinModifier NONE = new SinModifier() { };
}
