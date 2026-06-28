package model.game.shop;

import model.cards.Card;
import model.cards.consumables.Planets;
import model.cards.consumables.Tarots;
import model.cards.jokers.Jokers;

import java.util.random.RandomGenerator;

/**
 * Catalog-backed {@link ShopPool} for the card row: Jokers (rarity-weighted), Tarots, and Planets, in a
 * base-game-style mix (no playing cards by default). Costs come straight off each card. Draws use only the
 * supplied stream, so a shared seed mirrors offerings across runs. Spectrals are excluded until that catalog exists.
 */
public final class CatalogShopPool implements ShopPool {

    public static final CatalogShopPool INSTANCE = new CatalogShopPool();

    // card-row type split (out of 100)
    private static final int JOKER_WEIGHT = 72;
    private static final int TAROT_WEIGHT = 14;   // remainder is Planet


    private CatalogShopPool() { }

    @Override
    public Card roll(RandomGenerator stream) {
        int type = stream.nextInt(100);
        if (type < JOKER_WEIGHT)                return Jokers.weightedRandom(stream).make();
        if (type < JOKER_WEIGHT + TAROT_WEIGHT) return Tarots.random(stream).make();
        return Planets.random(stream).make();
    }
}
