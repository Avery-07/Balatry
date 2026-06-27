package model.game.player;

import model.game.rng.RngSource;

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-player counters that live beside a {@link Run}: the per-source occurrence counts that salt
 * emergent draws (e.g. glass shatter, lucky procs, effect-driven generation). One counter per
 * {@link RngSource}, created on demand, so adding a new randomized event needs no edit here.
 */
public final class PlayerStats {

    private final Map<RngSource, Integer> occurrences = new EnumMap<>(RngSource.class);

    /** Salt for the next draw on {@code source} (its 0-based occurrence index), then advances that source's counter. */
    public long nextSalt(RngSource source) {
        int n = occurrences.getOrDefault(source, 0);
        occurrences.put(source, n + 1);
        return n;
    }

    /** How many draws have been taken on {@code source} so far. Does not advance the counter. */
    public int getCount(RngSource source) {
        return occurrences.getOrDefault(source, 0);
    }
}
