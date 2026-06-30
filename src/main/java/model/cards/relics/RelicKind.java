package model.cards.relics;

/**
 * What a relic aims at, used by the resolver to drive targeting and the Aegis/Anger gate.
 *
 * <ul>
 *   <li>{@code OPPONENT} — a hostile effect on another seat (Aegis can negate it; Anger counts the targeting).</li>
 *   <li>{@code PLAYER} — names a seat that may be self or another; treated as hostile only when it is another seat.</li>
 *   <li>{@code SELF} — acts on the caster (no target selection).</li>
 *   <li>{@code GLOBAL} — table-level, affecting shared state (no target selection).</li>
 * </ul>
 *
 * <p>The hostile gate keys off the resolved target identity, not this kind, so the enum is advisory:
 * it documents intent and lets callers/UI validate a selection. Effects still guard their own inputs.
 */
public enum RelicKind { OPPONENT, PLAYER, SELF, GLOBAL }
