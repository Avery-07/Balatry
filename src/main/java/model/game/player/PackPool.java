package model.game.player;

import model.cards.packs.BoosterPack;

import java.util.random.RandomGenerator;

/** Supplies booster packs for the shop's pack row. */
@FunctionalInterface
public interface PackPool {
    BoosterPack roll(RandomGenerator stream);
}
