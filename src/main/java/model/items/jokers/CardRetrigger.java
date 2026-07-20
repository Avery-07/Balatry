package model.items.jokers;

import model.items.DeckCard;
import model.game.player.Run;

/** A joker's retrigger contribution for one card in the current scoring phase. */
@FunctionalInterface
public interface CardRetrigger {
    /** Extra times {@code self} retriggers {@code card} this phase (0 = none). */
    int extra(Run run, JokerCard self, DeckCard card);

    CardRetrigger NONE = (run, self, card) -> 0;
}
