package model.cards.jokers;

/**
 * Declarative capabilities for jokers whose effect is a <em>special interaction</em> the engine queries by
 * presence rather than firing on a {@link model.game.scoring.Trigger}. These replace the former
 * dispatch-by-display-name checks (which were both fragile and skipped the debuff gate); the engine asks
 * {@link JokerCard#hasActiveTrait} instead, so a debuffed joker's trait is automatically inert — consistent
 * with how every {@code Trigger}-based effect is already gated.
 *
 * <p>Add a constant here, set it on the relevant spec in {@link Jokers}, and query it where the interaction is
 * resolved. No new field on the spec, no new string compare.
 */
public enum JokerTrait {

    /** Chicot: while owned and not debuffed, the active boss blind is suppressed for this player. */
    DISABLES_BOSS,

    /** Mr. Bones: while owned and not debuffed, can save a failed blind (its own charge logic still applies). */
    PREVENTS_LOSS
}
