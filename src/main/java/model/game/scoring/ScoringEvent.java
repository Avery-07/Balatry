package model.game.scoring;

import java.math.BigDecimal;

/**
 * One step of a hand's scoring, recorded in the order the engine performed it — the timeline the client replays
 * as the trigger/effect animation (the source pops, a small square shows what it did).
 *
 * <p>Two properties make this safe to animate against. First, every event names its {@code sourceId} — a
 * {@code Card.id()} — so the client knows exactly which card or joker to pop, with {@link #NO_SOURCE} for the
 * few contributions no card owns (the hand's base value, the ante sin's transform). Second, every event carries
 * the running {@code chipsAfter}/{@code multAfter}: the client displays those rather than re-deriving the
 * arithmetic, so the animation cannot drift from the score the model actually banked, and the last event's
 * totals are by construction the real result.
 *
 * @param sourceId   the acting card's {@code Card.id()}, or {@link #NO_SOURCE}
 * @param sourceName what the source is called, for the tooltip/label
 * @param kind       what happened
 * @param amount     the magnitude: chips/mult added, the multiplier for {@link Kind#XMULT}, dollars for
 *                   {@link Kind#MONEY}, the extra-pass count for {@link Kind#RETRIGGER}, else zero
 * @param chipsAfter the session's chips once this event had been applied
 * @param multAfter  the session's mult once this event had been applied
 */
public record ScoringEvent(int sourceId, String sourceName, Kind kind,
                           BigDecimal amount, BigDecimal chipsAfter, BigDecimal multAfter) {

    /** The source id used when no card owns the contribution (the hand's base, a sin's transform). */
    public static final int NO_SOURCE = -1;

    /** What an event did — the vocabulary of effect squares the client can draw. */
    public enum Kind {
        BASE,       // the hand type's level seeds chips and mult
        CHIPS,      // +chips
        MULT,       // +mult
        XMULT,      // xmult
        MONEY,      // +$ earned mid-scoring (gold seal, Lucky, money jokers)
        RETRIGGER,  // a card or joker is about to fire extra times
        DESTROYED,  // a card broke while scoring (glass)
        BALANCE     // the Plasma deck averages chips and mult at the end
    }

    /** Whether this event has a card to pop; false for the base value and other ownerless steps. */
    public boolean hasSource() { return sourceId != NO_SOURCE; }
}
