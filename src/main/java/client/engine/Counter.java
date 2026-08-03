package client.engine;

import java.util.function.DoubleUnaryOperator;

/**
 * An animated numeric readout: the displayed value glides toward the real one (the Balatro count-up), and every
 * increase spikes a <em>pop</em> scale that decays back to rest — the renderer multiplies its font size by
 * {@link #popScale()} so the number visibly thumps when it grows. Pure (no JavaFX), so the behaviour is
 * unit-tested; drawing only reads {@link #displayed()} and {@link #popScale()}.
 */
public final class Counter {

    /** How hard a change thumps the readout, and the spring that carries the thump back down — underdamped, so it
     *  overshoots into a small recoil and bounces to rest instead of decaying flatly. */
    private static final double POP_SPIKE = 0.42, POP_STIFFNESS = 260, POP_DAMPING = 16;

    private final Tween value;
    private final Spring pop = new Spring(0, POP_STIFFNESS, POP_DAMPING);   // 0 at rest; pushed up on an increase, springs back

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
        if (target > value.target()) pop.push(POP_SPIKE);
        value.retarget(target);
    }

    /** Jumps straight to {@code v} with no glide and no pop — for entering a new screen, not for scoring. */
    public void snap(double v) { value.snap(v); pop.snap(0); }

    public void advance(double dt) {
        value.advance(dt);
        pop.advance(dt);
    }

    /** The value the renderer should print right now (round it for display). */
    public double displayed() { return value.value(); }

    /** The size multiplier for the readout; 1 at rest, above 1 on the thump, dipping a touch below on the recoil. */
    public double popScale() { return 1 + pop.value(); }

    public boolean settled() { return value.done() && pop.settled(); }
}
