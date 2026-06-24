package model.cards.consumables;

import model.game.player.Run;

@FunctionalInterface
public interface ConsumableEffect {
    void consume(Run run, ConsumableCard self);
    ConsumableEffect NO_OP = (run, self) -> {};
}
