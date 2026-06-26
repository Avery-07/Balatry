package model.game.player;

import model.cards.Card;
import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.consumables.Planets;
import model.cards.jokers.Jokers;

import java.util.random.RandomGenerator;

/**
 * Catalog-backed {@link ShopPool}: draws real content (the {@link Jokers} slice, all {@link Planets},
 * and plain playing cards) using only the supplied stream, so a shared seed mirrors offerings across runs.
 * Replaces the {@link ShopPool#PLACEHOLDER} stub as the catalog grows; weights are provisional.
 */
public final class CatalogShopPool implements ShopPool {

    /** Shared instance; the pool holds no per-shop state. */
    public static final CatalogShopPool INSTANCE = new CatalogShopPool();

    private CatalogShopPool() { }

    @Override
    public Card roll(RandomGenerator stream) {
        int r = stream.nextInt(100);
        if (r < 50) {                       // ~50% playing card
            DeckCard card = new DeckCard(
                    Rank.values()[stream.nextInt(Rank.values().length)],
                    Suit.values()[stream.nextInt(Suit.values().length)]);
            card.setShopValue(1);
            return card;
        }
        if (r < 80) {                       // ~30% joker
            Jokers[] jokers = Jokers.values();
            return jokers[stream.nextInt(jokers.length)].make();
        }
        Planets[] planets = Planets.values();   // ~20% planet
        return planets[stream.nextInt(planets.length)].make();
    }
}
