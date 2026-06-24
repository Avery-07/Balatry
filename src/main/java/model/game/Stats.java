package model.game;

import model.game.rng.LuckEvent;

/**
 * Per-player bookkeeping that lives beside a {@link Run}. Today it holds the
 * <em>luck counters</em>; it is the home for any other per-player statistics that
 * accrue over a run (hands played, cards destroyed, money earned, …) as those are
 * needed, so callers reach a single place rather than scattering counters across
 * the model.
 * <p>
 * <strong>Luck counters.</strong> Each {@link LuckEvent} has its own field holding
 * an independent, monotonically increasing occurrence count. The count is the salt
 * fed to the keyed RNG, which is what makes the N-th occurrence of an event resolve
 * identically for every player on the shared seed (see {@link LuckEvent}). Counts
 * advance only when their event actually fires, so identical play produces
 * identical counts — and any divergence still reads the same fixed luck script,
 * just to a different depth.
 * <p>
 * The counters are named fields rather than a map so each is visible at a glance.
 * The {@link #nextSalt} / {@link #count} switches over {@link LuckEvent} are
 * exhaustive, so adding an event is a compile error until its counter is wired up.
 * <p>
 * Single-threaded by design, matching the authoritative server model; no
 * synchronization here.
 */
public final class PlayerStats {

    private int glassBreak;       // Glass shatter rolls
    private int luckyMult;        // Lucky +mult rolls
    private int luckyMoney;       // Lucky +money rolls
    private int wheelOfFortune;   // Wheel of Fortune edition rolls

    /**
     * Returns the salt for the <em>next</em> roll of {@code event} — the 0-based
     * index of this occurrence — and advances that counter. Each call consumes one
     * occurrence, so a retriggered card that rolls twice naturally draws two
     * consecutive salts.
     */
    public long nextSalt(LuckEvent event) {
        return switch (event) {
            case GLASS_BREAK      -> glassBreak++;
            case LUCKY_MULT       -> luckyMult++;
            case LUCKY_MONEY      -> luckyMoney++;
            case WHEEL_OF_FORTUNE -> wheelOfFortune++;
        };
    }

    /**
     * How many times {@code event} has fired so far — equivalently, the salt the
     * next roll would use. Read-only; does not advance the counter.
     */
    public int count(LuckEvent event) {
        return switch (event) {
            case GLASS_BREAK      -> glassBreak;
            case LUCKY_MULT       -> luckyMult;
            case LUCKY_MONEY      -> luckyMoney;
            case WHEEL_OF_FORTUNE -> wheelOfFortune;
        };
    }
}