package model.game.rng;

import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/** Production {@link Rng}: outcomes are a pure function of {@code (seed, source, salt)}, stable across machines and JVMs. */
public final class DeterministicRng implements Rng {

    private final long seed;

    public DeterministicRng(long seed) { this.seed = seed; }

    /** The canonical match seed this generator was derived from. */
    public long getSeed() { return seed; }

    @Override
    public double nextDouble(RngSource source, long salt) {
        return streamFor(source, salt).nextDouble();
    }

    @Override
    public int nextInt(RngSource source, long salt, int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive: " + bound);
        return streamFor(source, salt).nextInt(bound);
    }

    @Override
    public boolean chance(RngSource source, long salt, int numerator, int denominator) {
        if (denominator <= 0) throw new IllegalArgumentException("denominator must be positive: " + denominator);
        if (numerator <= 0) return false;
        if (numerator >= denominator) return true;
        return streamFor(source, salt).nextInt(denominator) < numerator;
    }

    @Override
    public RandomGenerator streamFor(RngSource source, long salt) {
        return new SplittableRandom(key(source, salt));
    }

    /** Mixes seed, source code and salt into a 64-bit stream seed. */
    private long key(RngSource source, long salt) {
        long z = seed + GOLDEN * (source.getCode() + 1L);
        z = mix64(z);
        z += GOLDEN * (salt * 2L + 1L);   // odd factor keeps distinct salts distinct pre-mix
        return mix64(z);
    }

    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    /** SplitMix64 finalizer (Stafford variant 13). */
    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}