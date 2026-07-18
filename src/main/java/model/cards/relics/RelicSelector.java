package model.cards.relics;

/**
 * The one choice a relic asks its caster for, beyond a seat. A client reads this to know which picker to show:
 * the seat question is answered by {@link RelicKind}, this answers "and what?".
 */
public enum RelicSelector {

    /** Nothing to choose (Pyre, Limos, Metabole, Mimesis, Aegis). */
    NONE,

    /** A card rank (Anathema). */
    RANK,

    /** A card suit (Miasma). */
    SUIT,

    /** A board position, which may hold a different joker for each victim (Katadesmos). */
    JOKER_SLOT,

    /** A poker hand type (Katabasis). */
    HAND_TYPE
}
