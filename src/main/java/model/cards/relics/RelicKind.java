package model.cards.relics;

/**
 * How a relic picks its victims. The resolver derives the target set from this, so an effect never
 * chooses seats for itself.
 *
 * <p>Relics are standings-driven: the caster picks the <em>selector</em> (a rank, a suit, a board slot,
 * a hand type), but who gets hit follows from the match standings. "Above" means strictly more points,
 * so a seat tied with the caster is never a target and seating order cannot decide aggression.</p>
 */
public enum RelicKind {

    /** Resolves on the caster; no targeting, no Aegis check (Mimesis, Aegis). */
    SELF,

    /** Resolves once on the table as a whole (Metabole). */
    GLOBAL,

    /** One freely chosen opponent, whatever the standings say (Pyre). */
    OPPONENT,

    /** One chosen seat that must be strictly above the caster (Limos, Harpax). */
    RIVAL,

    /** Every seat strictly above the caster; the caster chooses no seat (Anathema, Miasma, Katadesmos, Katabasis). */
    RIVALS
}
