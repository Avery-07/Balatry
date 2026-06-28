package model.game.shop;

import model.cards.Card;

import java.util.random.RandomGenerator;

/** Supplies one item for the shop's card row. Pluggable so the real {@link CatalogShopPool} can be swapped for tests. */
@FunctionalInterface
public interface ShopPool {

    /** Rolls one item using only {@code stream} (for determinism), with its shop value set. */
    Card roll(RandomGenerator stream);
}
