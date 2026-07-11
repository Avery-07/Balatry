package model.game.rng;

import java.util.random.RandomGenerator;

/** Deterministic, keyed randomness for a run. */
public interface Rng {

    /** Uniform double in [0.0, 1.0) for {@code (source, salt)}. */
    double nextDouble(RngSource source, long salt);

    /** Uniform int in [0, bound) for {@code (source, salt)}. {@code bound} must be positive. */
    int nextInt(RngSource source, long salt, int bound);

    /** {@code true} with probability {@code numerator / denominator} for {@code (source, salt)}. */
    boolean chance(RngSource source, long salt, int numerator, int denominator);

    /** A reproducible sub-stream for events needing several correlated draws (e.g. dealing a pack). */
    RandomGenerator streamFor(RngSource source, long salt);

    /** Folds structural coordinates into one salt. Order-sensitive, so keep a fixed coordinate order per call site. */
    static long combine(long... parts) {
        long h = 0xCBF29CE484222325L;          // FNV-1a 64-bit offset basis
        for (long p : parts) {
            h ^= p;
            h *= 0x00000100000001B3L;            // FNV-1a 64-bit prime
            h ^= (h >>> 29);                      // extra avalanche
        }
        return h;
    }
}