package model.game.shop;

import model.cards.Card;
import model.cards.consumables.Planets;
import model.cards.consumables.Spectrals;
import model.cards.consumables.Tarots;
import model.cards.jokers.Jokers;
import model.cards.relics.Relics;

import java.util.random.RandomGenerator;

/**
 * The Gluttony card-row pool: "shops contain all types of consumables." The consumable share is boosted and
 * split across all four types — Joker 60 / Tarot 10 / Planet 10 / Spectral 10 / Relic 10, versus the base
 * 72/14/14/0/0 of {@link CatalogShopPool} — so the sin is felt, not just technically true. Draws use only the
 * supplied stream, so a shared seed still mirrors offerings across seats.
 */
public final class GluttonyShopPool implements ShopPool {

    public static final GluttonyShopPool INSTANCE = new GluttonyShopPool();

    // card-row type split (cumulative, out of 100)
    private static final int JOKER = 60, TAROT = 70, PLANET = 80, SPECTRAL = 90;   // remainder is Relic

    private GluttonyShopPool() { }

    @Override
    public Card roll(RandomGenerator stream) {
        int type = stream.nextInt(100);
        if (type < JOKER)    return Jokers.weightedRandom(stream).make();
        if (type < TAROT)    return Tarots.random(stream).make();
        if (type < PLANET)   return Planets.random(stream).make();
        if (type < SPECTRAL) return Spectrals.random(stream).make();
        return Relics.random(stream).make();
    }
}
