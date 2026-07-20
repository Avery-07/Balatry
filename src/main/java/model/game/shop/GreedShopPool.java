package model.game.shop;

import model.items.Card;
import model.items.consumables.Planets;
import model.items.consumables.Tarots;
import model.items.jokers.Jokers;

import java.util.random.RandomGenerator;

/** The Greed card-row pool: the same type mix as {@link CatalogShopPool} (Joker 72 / Tarot 14 / Planet 14), but "jokers have increased rarity" — the rarity split shifts from the base 55/35/10 (C/U/R) to 30/45/25: uncommons become the norm and rares appear 2.5x as often. */
public final class GreedShopPool implements ShopPool {

    public static final GreedShopPool INSTANCE = new GreedShopPool();

    // card-row type split (out of 100), matching the base pool
    private static final int JOKER_WEIGHT = 72;
    private static final int TAROT_WEIGHT = 14;   // remainder is Planet

    // boosted joker rarity (cumulative out of 100): Common 30 / Uncommon 45 / Rare 25
    private static final int COMMON_CUMULATIVE = 30, UNCOMMON_CUMULATIVE = 75;

    private GreedShopPool() { }

    @Override
    public Card roll(RandomGenerator stream) {
        int type = stream.nextInt(100);
        if (type < JOKER_WEIGHT)
            return Jokers.weightedRandom(stream, COMMON_CUMULATIVE, UNCOMMON_CUMULATIVE).make();
        if (type < JOKER_WEIGHT + TAROT_WEIGHT) return Tarots.random(stream).make();
        return Planets.random(stream).make();
    }
}
