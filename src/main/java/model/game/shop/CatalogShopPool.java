package model.game.shop;

import model.items.Card;
import model.items.consumables.Planets;
import model.items.consumables.Spectrals;
import model.items.consumables.Tarots;
import model.items.jokers.JokerCard;
import model.items.jokers.Jokers;
import model.items.relics.Relics;
import model.modifiers.Edition;
import model.game.player.Run;
import model.game.player.Sleeve;

import java.util.random.RandomGenerator;

/**
 * Catalog-backed {@link ShopPool} for the card row: Jokers (rarity-weighted), Tarots, Planets, and Relics
 * (Balatry's multiplayer-facing cards), in a base-game-style mix (no playing cards by default). Several vouchers
 * bend this pool for their owner's shop: Tarot/Planet/Relic Merchant &amp; Tycoon add appearance weight; Hone/Glow
 * Up stamp editions onto shop jokers; Showman lets a joker you already own appear; Encore favours owned jokers.
 * The base split is 72/14/14/14 (Joker/Tarot/Planet/Relic); the Merchant/Tycoon bonuses only add on top, so a shop
 * with none of those vouchers still rolls the fixed base mix and replays stay seed-deterministic.
 *
 * <p>Two loadout choices also bend the Planet band: the Ghost deck opens it to Spectrals, the Celestial sleeve
 * closes it entirely. The follow-up roll is drawn either way, so stream use stays loadout-independent.
 */
public final class CatalogShopPool implements ShopPool {

    public static final CatalogShopPool INSTANCE = new CatalogShopPool();

    // card-row type split (out of 114 at the defaults; vouchers add to the tarot/planet/relic weights)
    private static final int JOKER_WEIGHT = 72;
    private static final int TAROT_WEIGHT = 14;
    private static final int PLANET_WEIGHT = 14;
    private static final int RELIC_WEIGHT = 14;

    /** Share of card-row rolls that become a playing card while Magic Trick is active (out of 100). */
    private static final int MAGIC_TRICK_CARD_WEIGHT = 20;

    /** Share of the Planet band the Ghost deck turns into Spectrals (out of 100). */
    private static final int GHOST_SPECTRAL_SHARE = 50;

    /** Encore: chance a joker roll is a fresh copy of one the seat already owns (out of 100). */
    private static final int ENCORE_OWNED_CHANCE = 40;
    private static final int DEDUP_TRIES = 8;

    private CatalogShopPool() { }

    @Override
    public Card roll(RandomGenerator stream) { return roll(null, stream); }

    @Override
    public Card roll(Run run, RandomGenerator stream) {
        // Magic Trick: playing cards join the card row, rolling their modifiers at the same odds packs use (Illusion
        // boosts them). Rolled first and only when active, so a run without the voucher consumes the stream as before.
        if (run != null && run.shopMods().isMagicTrickActive() && stream.nextInt(100) < MAGIC_TRICK_CARD_WEIGHT) {
            model.items.DeckCard card = model.items.PlayingCards.rolled(stream, run.shopMods().isIllusionActive());
            card.setShopValue(1);
            return card;
        }

        int tarot  = TAROT_WEIGHT  + (run == null ? 0 : run.shopMods().getTarotWeightBonus());
        int planet = PLANET_WEIGHT + (run == null ? 0 : run.shopMods().getPlanetWeightBonus());
        int relic  = RELIC_WEIGHT  + (run == null ? 0 : run.shopMods().getRelicWeightBonus());
        int roll = stream.nextInt(JOKER_WEIGHT + tarot + planet + relic);
        if (roll < JOKER_WEIGHT)                  return joker(run, stream);
        if (roll < JOKER_WEIGHT + tarot)          return Tarots.random(stream).make();
        if (roll < JOKER_WEIGHT + tarot + planet) return planetBand(run, stream);
        return Relics.random(stream).make();
    }

    /**
     * A shop joker: Encore biases toward one the seat already owns; otherwise a fresh roll, de-duplicated against
     * owned jokers unless Showman/Encore allow them through; then Hone/Glow Up may stamp an edition.
     */
    private static Card joker(Run run, RandomGenerator stream) {
        if (run != null && run.shopMods().isEncore() && !run.getJokers().isEmpty() && stream.nextInt(100) < ENCORE_OWNED_CHANCE) {
            JokerCard sample = run.getJokers().get(stream.nextInt(run.getJokers().size()));
            JokerCard copy = new JokerCard(sample.getSpec(), sample.getShopValue());
            applyEdition(copy, run, stream);
            return copy;
        }
        boolean dedup = run != null && !run.shopMods().isShowman() && !run.shopMods().isEncore();
        JokerCard jc = Jokers.weightedRandom(stream).make();
        for (int i = 0; dedup && i < DEDUP_TRIES && owns(run, jc); i++) jc = Jokers.weightedRandom(stream).make();
        applyEdition(jc, run, stream);
        return jc;
    }

    private static boolean owns(Run run, JokerCard jc) {
        for (JokerCard j : run.getJokers()) if (j.getSpec() == jc.getSpec()) return true;
        return false;
    }

    /** Hone (rate 1) / Glow Up (rate 2): a chance to stamp a shiny edition (Foil/Holo/Poly) on a shop joker. */
    private static void applyEdition(JokerCard jc, Run run, RandomGenerator stream) {
        int rate = run == null ? 0 : run.shopMods().getEditionRate();
        if (rate <= 0 || stream.nextInt(100) >= (rate == 1 ? 20 : 35)) return;   // no roll at all when the voucher is absent
        int r = stream.nextInt(100);
        jc.apply(r < 60 ? Edition.FOIL : r < 90 ? Edition.HOLOGRAPHIC : Edition.POLYCHROME);
    }

    /** The Planet band: the Ghost deck opens it to Spectrals, the Celestial sleeve closes it; the follow-up roll is drawn either way. */
    private static Card planetBand(Run run, RandomGenerator stream) {
        int variant = stream.nextInt(100);
        boolean ghost = run != null && run.getDeckType().allowsSpectralInShop();
        boolean noPlanets = run != null && run.getSleeve() == Sleeve.CELESTIAL;
        if (ghost && (noPlanets || variant < GHOST_SPECTRAL_SHARE)) return Spectrals.random(stream).make();
        if (noPlanets) return Tarots.random(stream).make();   // Celestial: the Planet band falls back to Tarots
        return Planets.random(stream).make();
    }
}
