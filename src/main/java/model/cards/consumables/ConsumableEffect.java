package model.cards.consumables;

import model.game.player.Run;

/** A consumable's one-shot effect. Named {@code apply} to match {@link model.cards.jokers.JokerEffect}. */
@FunctionalInterface
public interface ConsumableEffect {
    void apply(Run run, ConsumableCard self);

    ConsumableEffect NO_OP = (run, self) -> { };
}
