package model.game.shop;

import model.items.Card;
import model.items.consumables.Planets;
import model.items.consumables.Spectrals;
import model.items.consumables.Tarots;
import model.items.jokers.Jokers;
import model.game.player.Run;
import model.game.player.Sleeve;

import java.util.random.RandomGenerator;

/**
 * Catalog-backed {@link ShopPool} for the card row: Jokers (rarity-weighted), Tarots, and Planets, in a
 * base-game-style mix (no playing cards by default).
 *
 * <p>Two loadout choices bend the mix, and both act on the Planet band. The Ghost deck opens it to Spectral
 * cards, which reach a shop no other way; the Celestial sleeve closes it entirely, the trade for the free hand
 * levels that sleeve grants each ante. The follow-up roll is drawn either way, so every seat consumes the same
 * amount of stream whatever it is playing — only how the roll is <em>read</em> depends on the loadout.
 */
public final class CatalogShopPool implements ShopPool {

    public static final CatalogShopPool INSTANCE = new CatalogShopPool();

    // card-row type split (out of 100)
    private static final int JOKER_WEIGHT = 72;
    private static final int TAROT_WEIGHT = 14;   // remainder is Planet

    /** Share of the Planet band the Ghost deck turns into Spectrals (out of 100). */
    private static final int GHOST_SPECTRAL_SHARE = 50;

    private CatalogShopPool() { }

    @Override
    public Card roll(RandomGenerator stream) { return roll(null, stream); }

    @Override
    public Card roll(Run run, RandomGenerator stream) {
        int type = stream.nextInt(100);
        if (type < JOKER_WEIGHT)                return Jokers.weightedRandom(stream).make();
        if (type < JOKER_WEIGHT + TAROT_WEIGHT) return Tarots.random(stream).make();

        int variant = stream.nextInt(100);
        boolean ghost = run != null && run.getDeckType().allowsSpectralInShop();
        boolean noPlanets = run != null && run.getSleeve() == Sleeve.CELESTIAL;

        if (ghost && (noPlanets || variant < GHOST_SPECTRAL_SHARE)) return Spectrals.random(stream).make();
        if (noPlanets) return Tarots.random(stream).make();   // Celestial: the Planet band falls back to Tarots
        return Planets.random(stream).make();
    }
}
