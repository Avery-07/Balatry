package model.cards.jokers;

/** Declarative capabilities for jokers whose effect is a special interaction the engine queries by presence rather than firing on a {@link model.game.scoring.Trigger}. */
public enum JokerTrait {

    /** Chicot: while owned and not debuffed, the active boss blind is suppressed for this player. */
    DISABLES_BOSS,

    /** Mr. Bones: while owned and not debuffed, can save a failed blind (its own charge logic still applies). */
    PREVENTS_LOSS
}
