package model.game.shop;

import model.cards.Card;
import model.game.player.Run;

import java.util.random.RandomGenerator;

/** Supplies one item for the shop's card row. Pluggable so the real {@link CatalogShopPool} can be swapped for tests. */
@FunctionalInterface
public interface ShopPool {

    /** Rolls one item using only {@code stream} (for determinism), with its shop value set. */
    Card roll(RandomGenerator stream);

    /**
     * Rolls for a specific seat, so a pool can honour what that seat's deck and sleeve allow (the Ghost deck lets
     * Spectrals appear; the Celestial sleeve bars Planets). Pools that do not care — the scripted ones tests
     * inject — inherit the seat-blind roll and behave exactly as before.
     */
    default Card roll(Run run, RandomGenerator stream) { return roll(stream); }
}
