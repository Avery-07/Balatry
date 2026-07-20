package model.game;

/** Base-game chip requirements per ante and blind (white-stake values). Specific boss blinds will later override the boss multiplier. */
public final class BlindTargets {

    // Small-blind requirement per ante; index 0 is unused so ante N maps to ANTE_BASE[N].
    private static final long[] ANTE_BASE = { 0, 300, 800, 2_000, 5_000, 11_000, 20_000, 35_000, 50_000 };

    private BlindTargets() { }

    /** Chips required to clear {@code blind} in {@code ante} at the White Stake. */
    public static long target(int ante, Blind blind) {
        long base = anteBase(ante);
        return switch (blind) {
            case SMALL -> base;
            case BIG   -> base * 3 / 2;   // 1.5x
            case BOSS  -> base * 2;       // 2x (default; specific bosses override)
        };
    }

    /**
     * Chips required to clear {@code blind} in {@code ante} at {@code stake}. Stakes are per-seat, so this is the
     * form every gameplay path should use — the White-stake {@link #target(int, Blind)} is the baseline it scales.
     */
    public static long target(int ante, Blind blind, Stake stake) {
        long base = target(ante, blind);
        return stake == null ? base : stake.scaleTarget(base, ante);
    }

    private static long anteBase(int ante) {
        if (ante < 1) throw new IllegalArgumentException("ante must be >= 1: " + ante);
        if (ante < ANTE_BASE.length) return ANTE_BASE[ante];
        // Endless: placeholder geometric continuation past ante 8 until the real scaling formula lands.
        long base = ANTE_BASE[ANTE_BASE.length - 1];
        for (int a = ANTE_BASE.length; a <= ante; a++) base = base * 3 / 2;
        return base;
    }
}