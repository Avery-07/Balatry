package client.engine;

import java.util.function.DoubleUnaryOperator;

/**
 * An animated numeric readout: the displayed value glides toward the real one (the Balatro count-up), and every
 * increase spikes a <em>pop</em> scale that decays back to rest — the renderer multiplies its font size by
 * {@link #popScale()} so the number visibly thumps when it grows. Pure (no JavaFX), so the behaviour is
 * unit-tested; drawing only reads {@link #displayed()} and {@link #popScale()}.
 */
public final class Counter {

    /** How hard a change thumps the readout, and how quickly the thump subsides (per second). */
    private static final double POP_SPIKE = 0.45, POP_DECAY = 3.0;

    private final Tween value;
    private double pop;   // 0 at rest; spikes on an increase and decays

    public Counter(double initial, double durationSeconds, DoubleUnaryOperator ease) {
        this.value = new Tween(initial, durationSeconds, ease);
    }

    /**
     * Points the readout at a new real value. An increase pops; a decrease (a reset to 0 at round start, a spent
     * dollar) just glides down without fanfare. Retargeting the same value is a no-op, so callers can feed the
     * snapshot's value every frame.
     */
    public void retarget(double target) {
        if (target == value.target()) return;
        if (target > value.target()) pop = POP_SPIKE;
        value.retarget(target);
    }

    /** Jumps straight to {@code v} with no glide and no pop — for entering a new screen, not for scoring. */
    public void snap(double v) { value.snap(v); pop = 0; }

    public void advance(double dt) {
        value.advance(dt);
        if (dt > 0) pop = Math.max(0, pop - POP_DECAY * dt);
    }

    /** The value the renderer should print right now (round it for display). */
    public double displayed() { return value.value(); }

    /** The size multiplier for the readout, {@code >= 1}; 1 at rest. */
    public double popScale() { return 1 + pop; }

    public boolean settled() { return value.done() && pop == 0; }
}
