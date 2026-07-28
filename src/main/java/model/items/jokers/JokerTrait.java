package model.items.jokers;

/** Declarative capabilities for jokers whose effect is a special interaction the engine queries by presence rather than firing on a {@link model.game.scoring.Trigger}. */
public enum JokerTrait {

    /** Chicot: while owned and not debuffed, the active boss blind is suppressed for this player. */
    DISABLES_BOSS,

    /** Mr. Bones: while owned and not debuffed, can save a failed blind (its own charge logic still applies). */
    PREVENTS_LOSS,

    /**
     * The joker's effect reads or writes other seats' state (money, jokers, standings), so two otherwise-identical
     * seats can legitimately diverge when both own it. Marked so seat-mirroring test harnesses can exclude it.
     */
    SEAT_COUPLING,

    // --- HandEvaluator flexibility: queried by the caller (Round) to configure how a played hand is classified ---

    /** Four Fingers: flushes and straights need only four cards. */
    FOUR_FINGERS,

    /** Shortcut: straights may skip a single rank per step (2-4-6-8-10 is a straight). */
    SHORTCUT,

    /** Smeared Joker: hearts and diamonds count as one suit, spades and clubs as one, for flush purposes. */
    SMEARED,

    /** Splash: every played card scores, not just the cards forming the hand. */
    SPLASH,

    /** Dyscalculie: every card also counts as the rank above (Ace as 2) when classifying the hand. */
    DYSCALCULIA,

    /** Oops! All 6s: doubles every "X in Y" probability the run rolls (stacks — each copy doubles again). */
    PROBABILITY_DOUBLER,

    /** Pareidolia: every card counts as a face card (read through {@link model.game.player.Run#isFaceCard}). */
    PAREIDOLIA
}
