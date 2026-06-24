package model.cards.jokers;

import model.game.player.Run;

@FunctionalInterface
public interface JokerEffect {
    void apply(Run run, JokerCard self);
    JokerEffect NO_OP = (run, self) -> {};
}
