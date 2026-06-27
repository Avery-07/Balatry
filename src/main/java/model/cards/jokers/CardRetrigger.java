package model.cards.jokers;

import model.cards.DeckCard;
import model.game.player.Run;

/**
 * A joker's retrigger contribution for one card in the current scoring phase.
 * The engine sums every joker's contribution per card, then repeats that card's scoring sub-sequence
 * in place (immediately), so retriggers resolve before the engine advances to the next card.
 */
@FunctionalInterface
public interface CardRetrigger {
    /** Extra times {@code self} retriggers {@code card} this phase (0 = none). */
    int extra(Run run, JokerCard self, DeckCard card);

    CardRetrigger NONE = (run, self, card) -> 0;
}
